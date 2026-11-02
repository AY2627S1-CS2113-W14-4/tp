package stockholm.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.order.ExportOrder;
import stockholm.order.ImportOrder;
import stockholm.order.Order;
import stockholm.order.OrderState;
import stockholm.order.TransferOrder;
import stockholm.parser.Parser;
import stockholm.state.AppState;

/** Tests that an oversized stock merge fails without changing stock or an order's state. */
class StockOverflowTest {
    private static final String MAX_COUNT = BigDecimal.valueOf(Double.MAX_VALUE).toPlainString();

    @Test
    public void itemAdd_overflow_keepsExistingStock() throws StockHolmException {
        AppState state = new AppState();
        Inventory shop = addInventory(state, "Shop", true);
        Item pen = new Item("Pen", "", Double.MAX_VALUE);
        shop.addItem(pen);

        StockHolmException error = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("item add Pen --count=" + MAX_COUNT), state));

        assertEquals("Total stock count is too large for item: Pen.", error.getMessage());
        assertSame(pen, shop.findItem("Pen"));
        assertEquals(Double.MAX_VALUE, pen.getCount());
    }

    @Test
    public void itemImport_overflowRejectsWholeFile(@TempDir Path tempDir) throws Exception {
        AppState state = new AppState();
        Inventory shop = addInventory(state, "Shop", true);
        Item pen = new Item("Pen", "", Double.MAX_VALUE);
        shop.addItem(pen);
        Path csv = tempDir.resolve("items.csv");
        Files.writeString(csv, "name,type,count,minCount\nNew,,1,\nPen,," + MAX_COUNT + ",\n");

        StockHolmException error = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("item import \"" + csv + "\""), state));

        assertTrue(error.getMessage().contains("CSV row 3: Total stock count is too large"));
        assertEquals(1, shop.getItems().size());
        assertSame(pen, shop.findItem("Pen"));
        assertEquals(Double.MAX_VALUE, pen.getCount());
    }

    @Test
    public void transfer_overflowAtDestination_keepsSourceStockAndCreatesNoOrder() throws StockHolmException {
        AppState state = new AppState();
        Inventory source = addInventory(state, "Source", true);
        Inventory destination = addInventory(state, "Destination", false);
        Item sourcePen = new Item("Pen", "", Double.MAX_VALUE);
        source.addItem(sourcePen);
        destination.addItem(new Item("Pen", "", Double.MAX_VALUE));

        assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("transfer 1 --to=Destination"), state));

        assertEquals(Double.MAX_VALUE, sourcePen.getCount());
        assertEquals(Double.MAX_VALUE, destination.findItem("Pen").getCount());
        assertTrue(state.getOrderHistory().getOrders().isEmpty());
    }

    @Test
    public void deliver_overflow_keepsOrderAwaitingDeliveryAndStockUnchanged() throws StockHolmException {
        AppState state = new AppState();
        Inventory shop = addInventory(state, "Shop", false);
        Item pen = new Item("Pen", "", Double.MAX_VALUE);
        shop.addItem(pen);
        Order order = new ImportOrder(new Item("Pen", "", Double.MAX_VALUE), shop,
                OrderState.WAITING_FOR_DELIVERY);
        state.getOrderHistory().add(order);

        assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("deliver 1"), state));

        assertEquals(OrderState.WAITING_FOR_DELIVERY, order.getState());
        assertEquals(Double.MAX_VALUE, pen.getCount());
    }

    @Test
    public void rejectOrCancel_overflowKeepsOrderStateAndStock() throws StockHolmException {
        for (String command : new String[]{"reject 1 Too large", "cancel 1"}) {
            AppState state = new AppState();
            Inventory shop = addInventory(state, "Shop", false);
            Item pen = new Item("Pen", "", Double.MAX_VALUE);
            shop.addItem(pen);
            OrderState original = command.startsWith("reject")
                    ? OrderState.WAITING_APPROVAL : OrderState.WAITING_FOR_DELIVERY;
            Order order = new ExportOrder(new Item("Pen", "", Double.MAX_VALUE), shop, original);
            state.getOrderHistory().add(order);

            assertThrows(StockHolmException.class,
                    () -> Processor.process(Parser.parseCommand(command), state), command);

            assertEquals(original, order.getState());
            assertEquals(Double.MAX_VALUE, pen.getCount());
        }
    }

    @Test
    public void deleteDestination_overflowingRefundKeepsInventoriesAndOrder() throws StockHolmException {
        AppState state = new AppState();
        Inventory source = addInventory(state, "Source", false);
        Inventory destination = addInventory(state, "Destination", false);
        Item pen = new Item("Pen", "", Double.MAX_VALUE);
        source.addItem(pen);
        Order order = new TransferOrder(new Item("Pen", "", Double.MAX_VALUE), source, destination,
                OrderState.WAITING_FOR_DELIVERY);
        state.getOrderHistory().add(order);

        assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("inv delete Destination"), state));

        assertSame(destination, state.findInventory("Destination"));
        assertEquals(OrderState.WAITING_FOR_DELIVERY, order.getState());
        assertEquals(Double.MAX_VALUE, pen.getCount());
    }

    /** Adds an inventory, optionally making it the current inventory for item or transfer commands. */
    private static Inventory addInventory(AppState state, String name, boolean enter) {
        Inventory inventory = new Inventory(name);
        state.getInventories().add(inventory);
        if (enter) {
            state.enterInventory(inventory);
        }
        return inventory;
    }
}

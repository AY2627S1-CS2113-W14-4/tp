package stockholm.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import stockholm.storage.OrderStorage;

/** Tests renaming an inventory and all of its order references. */
class InventoryRenameTest {
    @Test
    public void rename_existingInventory_keepsItemsAndUpdatesAllOrderEndpoints() throws StockHolmException {
        AppState state = new AppState();
        Inventory shop = new Inventory("Shop");
        Inventory warehouse = new Inventory("Warehouse");
        Item rice = new Item("Rice", "Food", 4);
        rice.setMinCount(2);
        shop.addItem(rice);
        state.getInventories().add(shop);
        state.getInventories().add(warehouse);
        Order imported = new ImportOrder(new Item("Rice", "Food", 1), shop, OrderState.DELIVERED);
        Order exported = new ExportOrder(new Item("Rice", "Food", 1), shop, OrderState.REJECTED, "Damaged");
        Order outgoing = new TransferOrder(new Item("Rice", "Food", 1), shop, warehouse);
        Order incoming = new TransferOrder(new Item("Rice", "Food", 1), warehouse, shop);
        state.getOrderHistory().add(imported);
        state.getOrderHistory().add(exported);
        state.getOrderHistory().add(outgoing);
        state.getOrderHistory().add(incoming);

        Result result = Processor.process(Parser.parseCommand("inv rename Shop Central Shop"), state);

        assertEquals("Renamed inventory: Shop -> Central Shop", result.message());
        assertNull(state.findInventory("Shop"));
        assertSame(shop, state.findInventory("Central Shop"));
        assertSame(rice, shop.findItem("Rice"));
        assertEquals(4, rice.getCount());
        assertEquals(2, rice.getMinCount());
        assertEquals("Central Shop", imported.getDst());
        assertEquals("Central Shop", exported.getSrc());
        assertEquals("Damaged", exported.getReason());
        assertEquals(OrderState.REJECTED, exported.getState());
        assertEquals("Central Shop", outgoing.getSrc());
        assertEquals("Warehouse", outgoing.getDst());
        assertEquals("Warehouse", incoming.getSrc());
        assertEquals("Central Shop", incoming.getDst());
    }

    @Test
    public void rename_unknownOrDuplicateName_changesNothing() throws StockHolmException {
        AppState state = new AppState();
        Inventory shop = new Inventory("Shop");
        state.getInventories().add(shop);
        state.getInventories().add(new Inventory("Warehouse"));
        Order order = new ImportOrder(new Item("Rice", "Food", 1), shop);
        state.getOrderHistory().add(order);

        assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("inv rename Ghost New Shop"), state));
        assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("inv rename Shop Warehouse"), state));
        assertSame(shop, state.findInventory("Shop"));
        assertEquals("Shop", order.getDst());
    }

    @Test
    public void rename_toDeletedTransferEndpoint_rejectsBeforeChangingHistory(@TempDir Path tempDir)
            throws Exception {
        AppState state = new AppState();
        Inventory source = new Inventory("A");
        Inventory destination = new Inventory("B");
        state.getInventories().add(source);
        state.getInventories().add(destination);
        Order transfer = new TransferOrder(new Item("Pen", "", 1), source, destination, OrderState.CANCELED);
        state.getOrderHistory().add(transfer);
        Processor.process(Parser.parseCommand("inv delete B"), state);

        StockHolmException error = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("inv rename A B"), state));

        assertEquals("Cannot rename A to B: transfer order 1 would have the same source and destination.",
                error.getMessage());
        assertSame(source, state.findInventory("A"));
        assertNull(state.findInventory("B"));
        assertEquals("A", transfer.getSrc());
        assertEquals("B", transfer.getDst());
        Path orderFile = tempDir.resolve("orders.csv");
        new OrderStorage(orderFile).save(state.getOrderHistory());
        assertTrue(Files.exists(orderFile));
    }
}

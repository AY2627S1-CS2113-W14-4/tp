package stockholm.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import stockholm.command.Command;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.order.Order;
import stockholm.order.OrderState;
import stockholm.order.TransferOrder;
import stockholm.parser.Parser;
import stockholm.state.AppState;

/**
 * Tests transfer orders and deleting their endpoints through {@link Processor#process(Command, AppState)}, using
 * commands built by {@link stockholm.parser.Parser} or directly.
 */
class TransferTest extends ProcessorTestBase {
    @Test
    public void process_deleteTransferDestination_refundsOnlyOpenIncomingOrders() throws StockHolmException {
        Inventory shop = enterNewShop();
        Inventory warehouse = new Inventory("Warehouse");
        inventories.add(warehouse);
        Item reserved = new Item("Rice", "Food", 2);
        reserved.setMinCount(5);
        for (OrderState orderState : OrderState.values()) {
            state.getOrderHistory().add(new TransferOrder(reserved, shop, warehouse, orderState));
        }
        state.leaveInventory();
        Processor.process(Parser.parseCommand("inv delete Warehouse"), state);
        assertNull(state.findInventory("Warehouse"));
        assertEquals(4.0, shop.findItem("Rice").getCount());
        assertEquals(5.0, shop.findItem("Rice").getMinCount());
        assertEquals(2.0, reserved.getCount());
    }

    @Test
    public void process_deleteTransferDestinationWithConflictingRefunds_changesNothing() throws StockHolmException {
        Inventory shop = enterNewShop();
        Inventory warehouse = new Inventory("Warehouse");
        inventories.add(warehouse);
        Order first = new TransferOrder(new Item("Rice", "Food", 2), shop, warehouse);
        Order conflicting = new TransferOrder(new Item("Rice", "Grain", 1), shop, warehouse);
        state.getOrderHistory().add(first);
        state.getOrderHistory().add(conflicting);
        state.leaveInventory();
        assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("inv delete Warehouse"), state));
        assertSame(warehouse, state.findInventory("Warehouse"));
        assertTrue(shop.getItems().isEmpty());
        assertEquals(OrderState.WAITING_APPROVAL, first.getState());
        assertEquals(OrderState.WAITING_APPROVAL, conflicting.getState());
    }

    @Test
    public void process_deleteTransferSource_cancelsWithoutAddingStockToDestination() throws StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Rice", "Food", 3));
        Inventory warehouse = new Inventory("Warehouse");
        inventories.add(warehouse);
        Processor.process(Parser.parseCommand("transfer 1 --to=Warehouse"), state);
        state.leaveInventory();
        Processor.process(Parser.parseCommand("inv delete Shop"), state);
        assertEquals(OrderState.CANCELED, orders().getFirst().getState());
        assertTrue(warehouse.getItems().isEmpty());
    }

    @Test
    public void process_transferOutside_reservesSnapshotAndListsBothInventories() throws StockHolmException {
        Inventory source = enterNewShop();
        Item rice = new Item("Rice", "Food", 3);
        source.addItem(new Item("Pen", "", 10));
        source.addItem(rice);
        Inventory destination = new Inventory("Warehouse");
        inventories.add(destination);
        state.leaveInventory();

        Result result = Processor.process(Parser.parseCommand(
                "transfer 2 --count=1.5 --from=Shop --to=Warehouse"), state);

        assertEquals("Transfer order created:\nItem name: Rice\nItem type: Food\nItem count: 1.5", result.message());
        assertEquals(1.5, rice.getCount());
        assertTrue(destination.getItems().isEmpty());
        assertFalse(state.isInsideInventory());
        assertEquals(1, orders().size());
        assertTrue(orders().getFirst() instanceof TransferOrder);
        assertEquals(OrderState.WAITING_APPROVAL, orders().getFirst().getState());
        rice.addCount(1);
        assertEquals(1.5, orders().getFirst().getItem().getCount());
        assertTrue(Processor.process(Parser.parseCommand("order list"), state).message()
                .contains("1. Transfer order (from Shop to Warehouse)"));
    }

    @Test
    public void process_transferOmittedEndpoint_usesCurrentInventory() throws StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Rice", "Food", 3));
        Inventory warehouse = new Inventory("Warehouse");
        warehouse.addItem(new Item("Pen", "", 2));
        inventories.add(warehouse);

        Processor.process(Parser.parseCommand("transfer 1 --to=Warehouse"), state);
        assertTrue(shop.getItems().isEmpty());
        Processor.process(Parser.parseCommand("transfer 1 --from=Warehouse"), state);
        assertTrue(warehouse.getItems().isEmpty());
        assertSame(shop, state.getCurrentInventory());
        assertEquals("Shop", orders().get(0).getSrc());
        assertEquals("Shop", orders().get(1).getDst());
    }

    @Test
    public void process_transferExplicitEndpointsInsideAnotherInventory_keepsCurrentInventory()
            throws StockHolmException {
        Inventory current = enterNewShop();
        Inventory source = new Inventory("Source");
        source.addItem(new Item("Rice", "Food", 1));
        inventories.add(source);
        inventories.add(new Inventory("Destination"));
        Processor.process(Parser.parseCommand("transfer 1 --from=Source --to=Destination"), state);
        assertSame(current, state.getCurrentInventory());
        assertTrue(source.getItems().isEmpty());
        assertEquals("Source", orders().getFirst().getSrc());
    }

    @Test
    public void process_transferInvalidInventoriesOrStock_changesNothing() throws StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Rice", "Food", 3));
        Inventory warehouse = new Inventory("Warehouse");
        warehouse.addItem(new Item("Rice", "Grain", 2));
        inventories.add(warehouse);
        for (String input : new String[]{"transfer 1", "transfer 1 --to=Shop",
            "transfer 1 --from=Missing --to=Warehouse", "transfer 1 --to=Missing",
            "transfer 2 --to=Warehouse", "transfer 1 --count=4 --to=Warehouse", "transfer 1 --to=Warehouse"}) {
            assertThrows(StockHolmException.class, () -> Processor.process(Parser.parseCommand(input), state), input);
            assertEquals(3.0, shop.findItem("Rice").getCount());
            assertEquals(2.0, warehouse.findItem("Rice").getCount());
            assertTrue(orders().isEmpty());
        }
        state.leaveInventory();
        for (String input : new String[]{"transfer 1", "transfer 1 --to=Warehouse", "transfer 1 --from=Shop"}) {
            assertThrows(StockHolmException.class, () -> Processor.process(Parser.parseCommand(input), state), input);
        }
    }

    @Test
    public void process_transferDecimalCounts_removesItemWithoutResidueAndAlerts() throws StockHolmException {
        Inventory shop = enterNewShop();
        Item rice = new Item("Rice", "Food", 0.3);
        rice.setMinCount(0.3);
        shop.addItem(rice);
        inventories.add(new Inventory("Warehouse"));
        Result partial = Processor.process(Parser.parseCommand("transfer 1 --count=0.1 --to=Warehouse"), state);
        assertEquals(0.2, rice.getCount());
        assertTrue(partial.message().contains("Low stock: Rice has 0.2 left"));
        Result all = Processor.process(Parser.parseCommand("transfer 1 --count=0.2 --to=Warehouse"), state);
        assertTrue(shop.getItems().isEmpty());
        assertTrue(all.message().contains("Out of stock: Rice has run out"));
        assertEquals(0.1, orders().get(0).getItem().getCount());
        assertEquals(0.2, orders().get(1).getItem().getCount());
    }

    @Test
    public void process_transferDeliver_mergesOnlyOnDeliveryAndKeepsDestinationMinimum() throws StockHolmException {
        Inventory shop = enterNewShop();
        Item rice = new Item("Rice", "Food", 3);
        rice.setMinCount(5);
        shop.addItem(rice);
        Inventory warehouse = new Inventory("Warehouse");
        Item destinationRice = new Item("Rice", "Food", 1);
        destinationRice.setMinCount(2);
        warehouse.addItem(destinationRice);
        inventories.add(warehouse);
        Processor.process(Parser.parseCommand("transfer 1 --count=2 --to=Warehouse"), state);
        assertThrows(StockHolmException.class, () -> Processor.process(Parser.parseCommand("deliver 1"), state));
        Processor.process(Parser.parseCommand("approve 1"), state);
        assertEquals(1.0, destinationRice.getCount());
        state.leaveInventory();
        Processor.process(Parser.parseCommand("deliver 1"), state);
        assertEquals(3.0, destinationRice.getCount());
        assertEquals(2.0, destinationRice.getMinCount());
        assertEquals(1.0, rice.getCount());
        assertEquals(2.0, orders().getFirst().getItem().getCount());
        assertEquals(OrderState.DELIVERED, orders().getFirst().getState());
        assertThrows(StockHolmException.class, () -> Processor.process(Parser.parseCommand("deliver 1"), state));
        assertEquals(3.0, destinationRice.getCount());
    }

    @Test
    public void process_transferRejectAndCancel_refundsSourceAndRestoresItsMinimum() throws StockHolmException {
        for (boolean cancel : new boolean[]{false, true}) {
            setUp();
            Inventory shop = enterNewShop();
            Item rice = new Item("Rice", "Food", 3);
            rice.setMinCount(5);
            shop.addItem(rice);
            Inventory warehouse = new Inventory("Warehouse");
            inventories.add(warehouse);
            Processor.process(Parser.parseCommand("transfer 1 --to=Warehouse"), state);
            assertTrue(shop.getItems().isEmpty());
            if (cancel) {
                Processor.process(Parser.parseCommand("approve 1"), state);
            }
            state.leaveInventory();
            String transition = cancel ? "cancel 1" : "reject 1 Damaged";
            Processor.process(Parser.parseCommand(transition), state);
            assertEquals(3.0, shop.findItem("Rice").getCount());
            assertEquals(5.0, shop.findItem("Rice").getMinCount());
            assertTrue(warehouse.getItems().isEmpty());
            assertEquals(cancel ? OrderState.CANCELED : OrderState.REJECTED, orders().getFirst().getState());
            assertThrows(StockHolmException.class,
                    () -> Processor.process(Parser.parseCommand(transition), state));
            assertEquals(3.0, shop.findItem("Rice").getCount());
        }
    }

    @Test
    public void process_transferNewDestinationItem_doesNotCopySourceMinimum() throws StockHolmException {
        Inventory shop = enterNewShop();
        Item rice = new Item("Rice", "Food", 3);
        rice.setMinCount(5);
        shop.addItem(rice);
        Inventory warehouse = new Inventory("Warehouse");
        inventories.add(warehouse);
        Processor.process(Parser.parseCommand("transfer 1 --to=Warehouse"), state);
        Processor.process(Parser.parseCommand("approve 1"), state);
        Processor.process(Parser.parseCommand("deliver 1"), state);
        Item delivered = warehouse.findItem("Rice");
        assertEquals(0.0, delivered.getMinCount());
        delivered.addCount(2);
        assertEquals(3.0, orders().getFirst().getItem().getCount());
    }

    @Test
    public void process_transferChangedDestinationType_deliveryFailureKeepsStateAndStock() throws StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Rice", "Food", 3));
        Inventory warehouse = new Inventory("Warehouse");
        inventories.add(warehouse);
        Processor.process(Parser.parseCommand("transfer 1 --to=Warehouse"), state);
        Processor.process(Parser.parseCommand("approve 1"), state);
        warehouse.addItem(new Item("Rice", "Grain", 1));
        assertThrows(StockHolmException.class, () -> Processor.process(Parser.parseCommand("deliver 1"), state));
        assertEquals(OrderState.WAITING_FOR_DELIVERY, orders().getFirst().getState());
        assertEquals(1.0, warehouse.findItem("Rice").getCount());
        assertTrue(shop.getItems().isEmpty());
    }

    @Test
    public void process_transferChangedSourceType_refundFailureKeepsStateAndStock() throws StockHolmException {
        for (boolean cancel : new boolean[]{false, true}) {
            setUp();
            Inventory shop = enterNewShop();
            shop.addItem(new Item("Rice", "Food", 3));
            inventories.add(new Inventory("Warehouse"));
            Processor.process(Parser.parseCommand("transfer 1 --to=Warehouse"), state);
            shop.addItem(new Item("Rice", "Grain", 1));
            if (cancel) {
                Processor.process(Parser.parseCommand("approve 1"), state);
            }
            assertThrows(StockHolmException.class,
                    () -> Processor.process(Parser.parseCommand(cancel ? "cancel 1" : "reject 1 Damaged"), state));
            assertEquals(cancel ? OrderState.WAITING_FOR_DELIVERY : OrderState.WAITING_APPROVAL,
                    orders().getFirst().getState());
            assertEquals(1.0, shop.findItem("Rice").getCount());
            assertTrue(state.findInventory("Warehouse").getItems().isEmpty());
        }
    }

    @Test
    public void process_transferMissingInventory_transitionChecksReceivingEndpoint() throws StockHolmException {
        Inventory shop = enterNewShop();
        Inventory warehouse = new Inventory("Warehouse");
        inventories.add(warehouse);
        Order transfer = new TransferOrder(new Item("Rice", "Food", 2), shop, warehouse);
        state.getOrderHistory().add(transfer);
        inventories.remove(shop);
        state.leaveInventory();
        assertThrows(StockHolmException.class, () -> Processor.process(Parser.parseCommand("reject 1 Lost"), state));
        assertEquals(OrderState.WAITING_APPROVAL, transfer.getState());
        Processor.process(Parser.parseCommand("approve 1"), state);
        inventories.remove(warehouse);
        assertThrows(StockHolmException.class, () -> Processor.process(Parser.parseCommand("deliver 1"), state));
        assertEquals(OrderState.WAITING_FOR_DELIVERY, transfer.getState());
        inventories.add(warehouse);
        Processor.process(Parser.parseCommand("deliver 1"), state);
        assertEquals(2.0, warehouse.findItem("Rice").getCount());
    }
}

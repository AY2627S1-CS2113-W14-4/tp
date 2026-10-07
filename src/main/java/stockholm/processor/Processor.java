package stockholm.processor;

import java.util.ArrayList;

import stockholm.exceptions.StockHolmException;
import stockholm.command.ArgKey;
import stockholm.command.Command;
import stockholm.inventory.Inventory;

/**
 * Runs a parsed {@link Command} against the application state and reports what happened
 * as a {@link Result}. It enforces business rules (e.g. no duplicate inventory names), but
 * it never reads input, parses strings or prints. Display is the Printer's job.
 *
 * <p>It holds no state of its own. The caller owns the data and passes it in.
 */
public class Processor {
    /**
     * Executes one command.
     *
     * @param command     a command produced by the Parser
     * @param inventories the application's inventories; the command may modify this list
     * @return the message to display and whether the program should exit
     * @throws StockHolmException if the command breaks a business rule
     */
    public static Result process(Command command, ArrayList<Inventory> inventories)
            throws StockHolmException {
        switch (command.type()) {
        case NO_OP:
            return new Result("", false);
        case QUIT:
            return new Result("", true);
        case INV_ADD:
            return addInventory(command.getArg(ArgKey.INV_NAME), inventories);
        case INV_DELETE:
            return deleteInventory(command.getArg(ArgKey.INV_NAME), inventories);
        case INV_LIST:
            return listInventories(inventories);
        default:
            throw new StockHolmException("Command not supported yet: " + command.type());
        }
    }

    private static Result addInventory(String name, ArrayList<Inventory> inventories)
            throws StockHolmException {
        if (findInventory(name, inventories) != null) {
            throw new StockHolmException("Inventory already exists: " + name);
        }
        inventories.add(new Inventory(name));
        return new Result("Added inventory: " + name, false);
    }

    private static Result deleteInventory(String name, ArrayList<Inventory> inventories)
            throws StockHolmException {
        Inventory target = findInventory(name, inventories);
        if (target == null) {
            throw new StockHolmException("No such inventory: " + name);
        }
        inventories.remove(target);
        return new Result("Deleted inventory: " + name, false);
    }

    private static Result listInventories(ArrayList<Inventory> inventories) {
        if (inventories.isEmpty()) {
            return new Result("No inventories yet.", false);
        }
        StringBuilder message = new StringBuilder("Inventories:");
        for (int i = 0; i < inventories.size(); i++) {
            message.append("\n").append(i + 1).append(". ").append(inventories.get(i).getName());
        }
        return new Result(message.toString(), false);
    }

    /** Returns the inventory with the given name, or {@code null} if there is none. */
    private static Inventory findInventory(String name, ArrayList<Inventory> inventories) {
        for (Inventory inventory : inventories) {
            if (inventory.getName().equals(name)) {
                return inventory;
            }
        }
        return null;
    }

    private static void enterInventory(String name) {

    }
}

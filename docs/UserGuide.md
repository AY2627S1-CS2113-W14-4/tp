# User Guide

## Introduction

StockHolm is a command-line app for managing several inventories, such as a warehouse and a shop, from the keyboard.
Stock enters and leaves an inventory through import and export orders that are approved and delivered, so every
change to stock is recorded.

## Quick Start

1. Ensure that you have Java 25 or above installed (`java -version`).
2. Download the latest `stockholm.jar` from [the releases page](https://github.com/AY2627S1-CS2113-W14-4/tp/releases).
3. Copy the file to the folder you want to keep your data in. StockHolm saves its data in a `data` folder next to
   where you run it, so this folder must not be write-protected (see the [FAQ](#faq)).
4. Open a terminal in that folder and run `java -jar stockholm.jar`. Run it from a terminal rather than by
   double-clicking the file: StockHolm has no window of its own, so double-clicking may do nothing or close at once.
5. Type a command at the `❯` prompt and press Enter. For example:
   * `inv add Shop` creates an inventory named Shop.
   * `enter Shop` enters it; the prompt becomes `Shop ❯`.
   * `item add Pen --count=10` adds 10 pens.
   * `item list` lists the items in Shop.
   * `bye` exits.
6. Type `help` at any time to see every command.

## Features

Notes about the command format:

* Words in `UPPER_CASE` are values you fill in, e.g. in `inv add NAME`, `NAME` can be `Shop`.
* Items in square brackets are optional, e.g. `stock [NAME]` can be used as `stock` or `stock Shop`.
* Options such as `--type=TYPE` and `--count=COUNT` can be given in any order. Values containing spaces must be
  in double quotes, e.g. `--type="Office Supplies"`.
* Counts are positive numbers and may have decimals, e.g. `2` or `2.5`.
* `INDEX` is the number shown next to an item in `item list`, or next to an order in `order list`.
* Commands and names are case-sensitive: `Shop` and `shop` are different inventories.
* Some commands only work inside an inventory (after `enter`), and `inv` commands only work outside one.
  `help` lists the commands by where they can be used.

### Viewing help: `help`
Lists every command and its format, grouped by where it can be used: anywhere, outside an inventory,
or inside an inventory. Works both inside and outside an inventory, and changes nothing.

Format: `help`

* `help` takes no arguments; anything after it is rejected.

Example of usage:

`help`

```
Commands (CAPS are values you fill in, [ ] is optional):
Anywhere:
  help - show this list
  quit - exit StockHolm (also: exit, close, bye)
  enter NAME - enter an inventory
  stock [NAME] [--below=COUNT] - show stock levels, of the current inventory by default
  find [KEYWORD] [--type=TYPE] - search items by name and/or type (in every inventory when outside one)
  restock [NAME] - list items below their minimum stock, of the current inventory by default
Outside an inventory:
  inv add NAME - create an inventory
  ...
```

### Managing inventories: `inv`

Creates, deletes and lists inventories. These commands only work outside an inventory; use `back` to leave one first.

Format: `inv add NAME`, `inv delete NAME`, `inv list`

* `NAME` may contain spaces, e.g. `inv add Main Warehouse`.
* Two inventories cannot have the same name.
* Deleting an inventory also deletes its items and orders.

Examples of usage:

`inv add Shop` → `Added inventory: Shop`

`inv list`

```
Inventories:
1. Shop
```

`inv delete Shop` → `Deleted inventory: Shop`

### Entering and leaving an inventory: `enter`, `back`

`enter` makes an inventory the current one, so item and order commands apply to it. The prompt shows its name,
e.g. `Shop ❯`. `back` leaves it.

Format: `enter NAME`, `back`

* `enter` works anywhere. Entering another inventory while inside one switches to it directly.
* `back` only works inside an inventory.

Example of usage:

`enter Shop` → `Entered inventory: Shop`

`back` → `Left inventory: Shop`

### Adding an item: `item add`

Adds an item to the current inventory. If an item with the same name exists, the count is added to it instead.

Format: `item add NAME [--type=TYPE] [--count=COUNT]`

* `NAME` may contain spaces. Put it in double quotes if it does, e.g. `item add "A4 Paper Case" --count=2`.
* Without `--count`, 1 is added. Without `--type`, the item has no type.
* When adding to an existing item, leave out `--type` or give the same type; a different type is rejected.

Examples of usage:

`item add "A4 Paper Case" --type=Stationery --count=2` → `Added item: A4 Paper Case (Stationery) x2`

`item add "A4 Paper Case" --count=3` → `Added 3 to existing item: A4 Paper Case (Stationery) x5`

### Importing items from CSV: `item import`

Adds many items to the current inventory from a UTF-8 CSV file. The first row must be exactly
`name,type,count,minCount`. Quote a field containing a comma or a double quote; write a double quote twice inside
a quoted field. `type` and `minCount` may be empty. An empty minimum means no minimum for a new item and leaves an
existing item's minimum unchanged.

Format: `item import CSV_PATH`

* Put the path in double quotes if it contains spaces, for example `item import "data/new items.csv"`.
* Names must be nonblank. Counts must be positive decimals, and minimums must be zero or positive decimals.
* Existing items with the same name follow `item add` rules: compatible counts are added, but a conflicting category
  rejects the import. A supplied minimum updates that item's minimum.
* Every row is checked before anything is added. One invalid row rejects the whole file and reports its row number.

Example file:

```csv
name,type,count,minCount
"Blue, Fine Pen",Stationery,3,5
Rice,Food,2.5,
```

`item import "data/new items.csv"` → `Imported 2 item row(s) into Shop.`

### Listing items: `item list`

Lists the items in the current inventory, numbered in the order they were added.

Format: `item list`

Example output:

```
Items in Shop:
1. A4 Paper Case (Stationery) x5
2. Rice (Food) x2.5
```

### Deleting an item: `item delete`

Deletes an item from the current inventory. The items after it are renumbered.

Format: `item delete INDEX`

Example of usage:

`item delete 2` → `Deleted item: Rice (Food) x2.5`

### Setting a minimum stock level: `item min`

Sets the minimum stock level of an item in the current inventory. Whenever the item falls below it, StockHolm
alerts you so you can reorder before it runs out.

Format: `item min INDEX COUNT`

* `INDEX` is the item's number in `item list`.
* `COUNT` is the minimum, a positive number such as `5` or `2.5`. Use `0` to remove the minimum.
* An item is **low on stock** when its count is below its minimum; an item exactly at its minimum is not low.
* If the item is already low when you set the minimum, you get an alert straight away.
* You are alerted again when an `export` takes the item below its minimum, and `stock` marks low items
  with `[LOW: min COUNT]`. Use `restock` to see everything that needs reordering.
* Minimums are saved with your data. If an item's stock reaches 0, the item is removed along with its minimum.

Examples of usage:

`item min 1 5` (item 1 is Blue Pen, with 3 left)

```
Set the minimum stock of Blue Pen to 5.
Low stock: Blue Pen has 3 left, below its minimum of 5. Reorder at least 2.
```

`item min 1 0` → `Cleared the minimum stock of Blue Pen.`

### Listing items to restock: `restock`

Lists the items of an inventory that are below their minimum stock level, with how many to reorder to get back
to the minimum. Works both inside and outside an inventory.

Format: `restock [NAME]`

* `NAME` is the inventory to check, with the same rules as for `stock`. Leave it out to check the current inventory.
* Items keep the same numbers as in `item list`.

Example of usage:

`restock Shop`

```
Items to restock in Shop:
1. Blue Pen (Stationery) x3 (min 5, reorder 2)
```

If no item is low: `Nothing to restock in Shop.` If no item has a minimum yet, StockHolm tells you to set one
with `item min`.

### Viewing stock levels: `stock`
Shows every item in an inventory with its count, followed by the total number of items and units.
Works both inside and outside an inventory, and never changes which inventory you are in.

Format: `stock [NAME] [--below=COUNT]`

* `NAME` is the inventory to show. Leave it out to show the inventory you are currently in.
* `NAME` may include spaces, with or without double quotes, e.g. `stock Main Warehouse --below=5` or
  `stock "Main Warehouse" --below=5`.
* `--below=COUNT` shows only the items whose count is below `COUNT` (a positive number, e.g. `5` or `2.5`),
  to find items that need restocking.
* Items keep the same numbers as in `item list`.
* Items below their [minimum stock level](#setting-a-minimum-stock-level-item-min) are marked `[LOW: min COUNT]`.

Examples of usage:

`stock Shop`

```
Stock levels in Shop:
1. A4 Paper Case (Stationery) x2
2. Rice (Food) x2.5
3. Blue Pen x1
Total: 3 item(s), 5.5 unit(s)
```

`stock Shop --below=2.5`

```
Items in Shop below 2.5:
1. A4 Paper Case (Stationery) x2
3. Blue Pen x1
```

### Finding items: `find`
Searches items by name, by type, or both, so you can find an item without scrolling through lists.
Works both inside and outside an inventory, and never changes anything.

Format: `find [KEYWORD] [--type=TYPE]`

* Give a `KEYWORD`, a `--type`, or both. With both, an item must match both.
* `KEYWORD` matches any item whose name **contains** it, ignoring upper/lower case: `find pen` finds `Blue Pen`
  and `Pen Refill`. It may contain spaces, e.g. `find blue pen`.
* `--type=TYPE` matches items whose type is exactly `TYPE`, ignoring case: `--type=stationery` finds items of type
  `Stationery`, but `--type=Station` does not.
* **Inside** an inventory, only that inventory is searched. **Outside** all inventories, every inventory is searched,
  and matches are grouped by inventory.
* Matches keep the same numbers as in `item list`, so you can use them straight away, e.g. with `export INDEX`.

Examples of usage:

`find pen` (outside any inventory)

```
Found 3 item(s) matching "pen":
Shop:
  1. Blue Pen (Stationery) x3
  3. Pen Refill (Stationery) x10
Main Warehouse:
  1. Pen x100
```

`find --type=Stationery` (inside Shop)

```
Found 2 item(s) of type Stationery:
Shop:
  1. Blue Pen (Stationery) x3
  3. Pen Refill (Stationery) x10
```

### Creating an import order: `import`

Creates an order to bring an item into the current inventory. Stock does not change until the order is
approved and delivered.

Format: `import NAME [--type=TYPE] [--count=COUNT]`

* `NAME`, `--type` and `--count` work as in `item add`. Without `--count`, the order is for 1.
* The new order is in the `WAITING_APPROVAL` state.
* If the export takes the item below its [minimum stock level](#setting-a-minimum-stock-level-item-min), a
  `Low stock` alert with the amount to reorder is shown under the order details. If the item runs out and had a
  minimum, an `Out of stock` alert is shown.

Example of usage:

`import Rice --type=Food --count=2.5`

```
Import order created:
Item name: Rice
Item type: Food
Item count: 2.5
```

### Creating an export order: `export`

Creates an order to send stock out of the current inventory. The stock is removed immediately, so it cannot be
exported twice.

Format: `export INDEX [--count=COUNT]`

* `INDEX` is the item's number in `item list`.
* Without `--count`, all of the item's stock is exported. A count larger than the stock is rejected.
* An item whose stock reaches 0 is removed, and the items after it are renumbered.
* The new order is in the `WAITING_APPROVAL` state.

Example of usage:

`export 1 --count=1`

```
Export order created:
Item name: Rice
Item type: Food
Item count: 1
```

### Listing orders: `order list`

Lists the orders of the current inventory in the order they were created, with their state.

Format: `order list`

Example output:

```
Orders in Shop:
1. Import order
   State: WAITING_APPROVAL
   Item name: Rice
   Item type: Food
   Item count: 2.5
```

An order moves through these states: `WAITING_APPROVAL` → (`approve`) → `WAITING_FOR_DELIVERY` →
(`deliver`) → `DELIVERED`, `WAITING_APPROVAL` → (`reject`) → `REJECTED`, or
`WAITING_APPROVAL` / `WAITING_FOR_DELIVERY` → (`cancel`) → `CANCELED`.

### Approving an order: `approve`

Approves an import or export order in the current inventory. Use `order list` to find its number.
Only an order in `WAITING_APPROVAL` can be approved. Its state then becomes `WAITING_FOR_DELIVERY`.
Approval does not change stock: an export reduces stock when created, while an import adds stock on delivery.

Format: `approve INDEX`

* Enter the inventory containing the order first, for example `enter Shop`.
* `INDEX` is the one-based number displayed by `order list` in that inventory.

Example:

```text
enter Shop
import Pen --count=2
order list
approve 1
order list
```

After `approve 1`, the second `order list` shows `State: WAITING_FOR_DELIVERY` for order 1.
An unknown order number or an order that has already left `WAITING_APPROVAL` is rejected.

### Rejecting an order: `reject`

Rejects an import or export order awaiting approval in the current inventory, with a reason.
Use `order list` to find its number.
Only an order in `WAITING_APPROVAL` can be rejected. Its state then becomes `REJECTED`.
For an import order, rejection records that the order is rejected; stock is unchanged.
For an export order, rejection returns the deducted stock back to the inventory.

Format: `reject INDEX REASON`

* Enter the inventory containing the order first, for example `enter Shop`.
* `INDEX` is the one-based number displayed by `order list` in that inventory.
* `REASON` is the explanation for rejecting the order, e.g. `Damaged packaging` or `Out of stock`.

Example:

```text
enter Shop
import Pen --count=2
order list
reject 1 Damaged packaging
order list
```

After `reject 1 Damaged packaging`, the second `order list` shows `State: REJECTED` and `Reason: Damaged packaging` for order 1.
An unknown order number or an order that has already left `WAITING_APPROVAL` is rejected.

### Canceling an order: `cancel`

Cancels an import or export order in the current inventory before it reaches `DELIVERED`.
Use `order list` to find its number.
Only an active order awaiting approval (`WAITING_APPROVAL`) or waiting for delivery (`WAITING_FOR_DELIVERY`)
can be canceled. Its state then becomes `CANCELED`.
For an import order, cancellation records that the order is canceled; stock is unchanged.
For an export order, cancellation returns the deducted stock back to the inventory.

Format: `cancel INDEX`

* Enter the inventory containing the order first, for example `enter Shop`.
* `INDEX` is the one-based number displayed by `order list` in that inventory.

Example:

```text
enter Shop
import Pen --count=2
order list
cancel 1
order list
```

After `cancel 1`, the second `order list` shows `State: CANCELED` for order 1.
An unknown order number or an order that has already reached `DELIVERED`, `REJECTED`, or `CANCELED` cannot be canceled.

### Delivering an order: `deliver`

Marks a waiting-for-delivery import or export order in the current inventory as delivered.
Use `order list` to find its number.
Only an order in `WAITING_FOR_DELIVERY` can be delivered. Its state then becomes `DELIVERED`.
For an import order, delivery adds the ordered goods to the inventory's stock (creating a new item or
merging into an existing item with the same name).
For an export order, delivery records that the goods have been delivered; stock was already reduced when
the export order was created.

Format: `deliver INDEX`

* Enter the inventory containing the order first, for example `enter Shop`.
* `INDEX` is the one-based number displayed by `order list` in that inventory.

Example:

```text
enter Shop
import Pen --count=2
order list
approve 1
deliver 1
order list
```

After `deliver 1`, the second `order list` shows `State: DELIVERED` for order 1, and `item list` shows `Pen x2`.
An unknown order number or an order that is not in `WAITING_FOR_DELIVERY` is rejected.

### Exiting: `bye`

Exits StockHolm.

Format: `bye` (also `quit`, `exit` or `close`)

### Saving the data

StockHolm saves automatically after every command that changes your data, and loads it again on the next start.
There is no need to save manually. The data is kept in a `data` folder inside the folder StockHolm is run from:

* `data/inventories.json`: inventories and their items. Saved after `inv add`, `inv delete`, `item add`,
  `item import`, `item delete`, `export`, `deliver`, `reject` and `cancel`.
* `data/orders.csv`: import and export orders, rejection reasons, and their states. Saved after `import`, `export`, `approve`,
  `reject`, `cancel`, `deliver` and `inv delete`. Older versions of StockHolm named this file `OrdersData.csv`; if only that file
  exists, StockHolm renames it to `orders.csv` on start, so your orders carry over.

On start, StockHolm shows how much it loaded, e.g. `Loaded 2 inventory(ies) and 3 order(s).` It always starts
outside any inventory.

If a save file has problems (for example after editing it by hand):

* An invalid inventory, item or order is skipped with a warning, and the rest still loads. The original file is
  first copied to a backup named after the time, e.g. `data/inventories.json.2026-10-13T14-03-22.bak`, so nothing
  is lost. Older backups are never replaced, and the same bad file is only backed up once.
* If `inventories.json` is not valid JSON at all, it is backed up the same way and StockHolm starts with no
  inventories. Saved orders still load.
* If a save file cannot be read at all (for example, no permission), StockHolm stops so it does not overwrite it.

## FAQ

**Q**: How do I transfer my data to another computer? 

**A**: Copy the whole `data` folder into the folder you run StockHolm from on the other computer.

**Q**: Nothing happens when I double-click `stockholm.jar`. What should I do?

**A**: StockHolm is a command-line app, so it needs a terminal. Open a terminal in the folder containing the file and
run `java -jar stockholm.jar`, as in the [Quick Start](#quick-start).

**Q**: StockHolm says `Changes were applied in memory, but inventories could not be saved`. What does that mean?

**A**: StockHolm could not write to the `data` folder, usually because the folder you ran it from is write-protected
(e.g. a system folder, or a read-only network drive). Your changes still work until you exit, but they will be lost
then. Exit, move `stockholm.jar` (and your `data` folder, if any) to a folder you can write to, such as your
Documents folder, and run it from there.

## Command Summary

| Action | Format | Where |
|---|---|---|
| Show help | `help` | Anywhere |
| Exit | `bye` (also `quit`, `exit`, `close`) | Anywhere |
| Enter an inventory | `enter NAME` | Anywhere |
| View stock levels | `stock [NAME] [--below=COUNT]` | Anywhere |
| Find items | `find [KEYWORD] [--type=TYPE]` | Anywhere |
| List items to restock | `restock [NAME]` | Anywhere |
| Add an inventory | `inv add NAME` | Outside an inventory |
| Delete an inventory | `inv delete NAME` | Outside an inventory |
| List inventories | `inv list` | Outside an inventory |
| Leave the inventory | `back` | Inside an inventory |
| Add an item | `item add NAME [--type=TYPE] [--count=COUNT]` | Inside an inventory |
| Import items from CSV | `item import CSV_PATH` | Inside an inventory |
| List items | `item list` | Inside an inventory |
| Delete an item | `item delete INDEX` | Inside an inventory |
| Set or clear a minimum stock level | `item min INDEX COUNT` | Inside an inventory |
| Create an import order | `import NAME [--type=TYPE] [--count=COUNT]` | Inside an inventory |
| Create an export order | `export INDEX [--count=COUNT]` | Inside an inventory |
| List orders | `order list` | Inside an inventory |
| Approve an order | `approve INDEX` | Inside the order's inventory |
| Reject an order | `reject INDEX REASON` | Inside the order's inventory |
| Cancel an order | `cancel INDEX` | Inside the order's inventory |
| Deliver an order | `deliver INDEX` | Inside the order's inventory |

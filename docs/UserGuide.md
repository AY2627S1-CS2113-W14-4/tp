# User Guide

## Introduction

StockHolm is a command-line app for managing several inventories, such as a warehouse and a shop, from the keyboard.
Stock enters and leaves an inventory through import and export orders that are approved and delivered, so every
change to stock is recorded.

## Quick Start

1. Ensure that you have Java 25 or above installed (`java -version`).
2. Download the latest `stockholm.jar` from [the releases page](https://github.com/AY2627S1-CS2113-W14-4/tp/releases).
3. Copy the file to the folder you want to keep your data in.
4. Open a terminal in that folder and run `java -jar stockholm.jar`.
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
Outside an inventory:
  inv add NAME - create an inventory
  ...
```

### Managing inventories: `inv`

Creates, deletes and lists inventories. These commands only work outside an inventory; use `back` to leave one first.

Format: `inv add NAME`, `inv delete NAME`, `inv list`

* `NAME` may contain spaces, e.g. `inv add Main Warehouse`.
* Two inventories cannot have the same name.
* Deleting an inventory also deletes its items. Its orders stay in `order list` as history.

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

Lists every order of every inventory, in the order they were created, with the inventory each one is for and
its state. Works both inside and outside an inventory. Orders of a deleted inventory are still listed.

Format: `order list`

Example output:

```
Orders:
1. Import order (to Shop)
   State: WAITING_APPROVAL
   Item name: Rice
   Item type: Food
   Item count: 2.5
2. Export order (from Warehouse)
   State: WAITING_FOR_DELIVERY
   Item name: Pen
   Item type:
   Item count: 3
```

The numbers are shared by all inventories: `approve 2` above approves Warehouse's export order, wherever you are.

An order moves through these states: `WAITING_APPROVAL` → (`approve`) → `WAITING_FOR_DELIVERY` →
(`deliver`) → `DELIVERED`, `WAITING_APPROVAL` → (`reject`) → `REJECTED`, or
`WAITING_APPROVAL` / `WAITING_FOR_DELIVERY` → (`cancel`) → `CANCELED`.

### Approving an order: `approve`

Approves an import or export order. Use `order list` to find its number.
Only an order in `WAITING_APPROVAL` can be approved. Its state then becomes `WAITING_FOR_DELIVERY`.
Approval does not change stock: an export reduces stock when created, while an import adds stock on delivery.

Format: `approve INDEX`

* Works both inside and outside an inventory.
* `INDEX` is the one-based number displayed by `order list`.

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

Rejects an import or export order awaiting approval, with a reason.
Use `order list` to find its number.
Only an order in `WAITING_APPROVAL` can be rejected. Its state then becomes `REJECTED`.
For an import order, rejection records that the order is rejected; stock is unchanged.
For an export order, rejection returns the deducted stock to the inventory it came from.

Format: `reject INDEX REASON`

* Works both inside and outside an inventory.
* `INDEX` is the one-based number displayed by `order list`.
* `REASON` is the explanation for rejecting the order, e.g. `Damaged packaging` or `Out of stock`.
  It may be wrapped in quotes (`"Damaged packaging"`) or given as `--reason="Damaged packaging"`; all three forms
  are the same.

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
An export order whose inventory has been deleted cannot be rejected either, since its stock has nowhere to go back to.

### Canceling an order: `cancel`

Cancels an import or export order before it reaches `DELIVERED`.
Use `order list` to find its number.
Only an active order awaiting approval (`WAITING_APPROVAL`) or waiting for delivery (`WAITING_FOR_DELIVERY`)
can be canceled. Its state then becomes `CANCELED`.
For an import order, cancellation records that the order is canceled; stock is unchanged.
For an export order, cancellation returns the deducted stock to the inventory it came from.

Format: `cancel INDEX`

* Works both inside and outside an inventory.
* `INDEX` is the one-based number displayed by `order list`.

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
An export order whose inventory has been deleted cannot be canceled either, since its stock has nowhere to go back to.

### Delivering an order: `deliver`

Marks a waiting-for-delivery import or export order as delivered.
Use `order list` to find its number.
Only an order in `WAITING_FOR_DELIVERY` can be delivered. Its state then becomes `DELIVERED`.
For an import order, delivery adds the ordered goods to the stock of the inventory it is for (creating a new
item or merging into an existing item with the same name).
For an export order, delivery records that the goods have been delivered; stock was already reduced when
the export order was created.

Format: `deliver INDEX`

* Works both inside and outside an inventory.
* `INDEX` is the one-based number displayed by `order list`.

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
An unknown order number or an order that is not in `WAITING_FOR_DELIVERY` is rejected. Delivering an import
order is also refused if its inventory has been deleted, since there is nowhere to put the goods.

### Exiting: `bye`

Exits StockHolm.

Format: `bye` (also `quit`, `exit` or `close`)

### Saving the data

StockHolm saves automatically after every command that changes your data, and loads it again on the next start.
There is no need to save manually. The data is kept in a `data` folder inside the folder StockHolm is run from:

* `data/inventories.json`: inventories and their items. Saved after `inv add`, `inv delete`, `item add`,
  `item delete`, `export`, `deliver`, `reject` and `cancel`.
* `data/orders.csv`: the order history: every import and export order, its state and any rejection reason, in the
  order they were created. Saved after `import`, `export`, `approve`, `reject`, `cancel` and `deliver`. Older versions of StockHolm named this file `OrdersData.csv`; if only that file
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

## Command Summary

| Action | Format | Where |
|---|---|---|
| Show help | `help` | Anywhere |
| Exit | `bye` (also `quit`, `exit`, `close`) | Anywhere |
| Enter an inventory | `enter NAME` | Anywhere |
| View stock levels | `stock [NAME] [--below=COUNT]` | Anywhere |
| Find items | `find [KEYWORD] [--type=TYPE]` | Anywhere |
| Add an inventory | `inv add NAME` | Outside an inventory |
| Delete an inventory | `inv delete NAME` | Outside an inventory |
| List inventories | `inv list` | Outside an inventory |
| Leave the inventory | `back` | Inside an inventory |
| Add an item | `item add NAME [--type=TYPE] [--count=COUNT]` | Inside an inventory |
| List items | `item list` | Inside an inventory |
| Delete an item | `item delete INDEX` | Inside an inventory |
| Create an import order | `import NAME [--type=TYPE] [--count=COUNT]` | Inside an inventory |
| Create an export order | `export INDEX [--count=COUNT]` | Inside an inventory |
| List orders | `order list` | Anywhere |
| Approve an order | `approve INDEX` | Anywhere |
| Reject an order | `reject INDEX REASON` | Anywhere |
| Cancel an order | `cancel INDEX` | Anywhere |
| Deliver an order | `deliver INDEX` | Anywhere |

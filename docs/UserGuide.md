# User Guide

## Introduction

{Give a product intro}

## Quick Start

{Give steps to get started quickly}

1. Ensure that you have Java 25 or above installed.
1. Down the latest version of `Duke` from [here](http://link.to/duke).

## Features 

{Give detailed description of each feature}

### Adding a todo: `todo`
Adds a new item to the list of todo items.

Format: `todo n/TODO_NAME d/DEADLINE`

* The `DEADLINE` can be in a natural language format.
* The `TODO_NAME` cannot contain punctuation.  

Example of usage: 

`todo n/Write the rest of the User Guide d/next week`

`todo n/Refactor the User Guide to remove passive voice d/13/04/2020`

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

### Approving an order: `approve`

Approves an import or export order in the current inventory. Use `order list` to find its number.
Only an order in `WAITING_APPROVAL` can be approved. Its state then becomes `WAITING_FOR_DELIVERY`.
Approval does not change stock: an export reduces stock when created, while an import adds stock on delivery.

Format: `approve ORDER_ID`

* Enter the inventory containing the order first, for example `enter Shop`.
* `ORDER_ID` is the one-based number displayed by `order list` in that inventory.

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

### Delivering an order: `deliver`

Marks a waiting-for-delivery import or export order in the current inventory as delivered.
Use `order list` to find its number.
Only an order in `WAITING_FOR_DELIVERY` can be delivered. Its state then becomes `DELIVERED`.
For an import order, delivery adds the ordered goods to the inventory's stock (creating a new item or
merging into an existing item with the same name).
For an export order, delivery records that the goods have been delivered; stock was already reduced when
the export order was created.

Format: `deliver ORDER_ID`

* Enter the inventory containing the order first, for example `enter Shop`.
* `ORDER_ID` is the one-based number displayed by `order list` in that inventory.

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

## FAQ

**Q**: How do I transfer my data to another computer? 

**A**: {your answer here}

## Command Summary

{Give a 'cheat sheet' of commands here}

* Add todo `todo n/TODO_NAME d/DEADLINE`
* View stock levels `stock [NAME] [--below=COUNT]`
* Approve an order `approve ORDER_ID` (inside the order's inventory)
* Deliver an order `deliver ORDER_ID` (inside the order's inventory)

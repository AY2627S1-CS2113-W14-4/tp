# Reyas Mohideen Fawaz - Project Portfolio Page

## Overview

StockHolm is a command-line application for managing stock across inventories. Import, export and transfer orders
record how goods move, while approval and delivery states control when an order may proceed.

I worked on order approval and inventory maintenance: editing item details, importing items from CSV, and renaming
inventories. I also reviewed teammates' changes to order handling, storage and documentation.

## Summary of Contributions

### Code contributed

[RepoSense code dashboard](https://nus-cs2113-ay2627-s1.github.io/tp-dashboard/?search=fawazrm23&breakdown=true)

### Enhancements implemented

* **Order approval** ([#21](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/21)):
  implemented approval of the numbered order shown by `order list` (currently `approve INDEX`). Only orders in
  `WAITING_APPROVAL` move to `WAITING_FOR_DELIVERY`; approval itself does not change stock. The command rejects
  missing, invalid and repeated approvals. I added parser and processor tests for the transition and its error cases.
* **Item detail editing** ([#58](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/58)):
  implemented `item edit INDEX [--name=NAME] [--type=TYPE]`. An edit keeps the item's count, minimum stock level and
  list position. It rejects a name already used in the inventory, and leaves past orders' item snapshots unchanged.
  Tests cover editing, duplicate names and restoration from saved inventory data.
* **Bulk CSV import** ([#59](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/59)):
  implemented `item import CSV_PATH` for UTF-8 files with `name,type,count,minCount` columns. The reader supports
  quoted commas and escaped quotes and reports the row of an invalid value. The command validates every row and
  previews all merges before changing stock, so one bad row or category conflict rejects the whole file. Valid rows
  add items or merge compatible items by name, then save through the existing inventory storage.
* **Inventory rename, pending review** ([#60](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/60)):
  implemented `inv rename OLD_NAME NEW_NAME` on an open PR. It keeps the inventory and its items in place while
  updating source and destination names in every order, including transfers and completed history. It rejects
  unknown or duplicate names before changing data. Tests cover order references and persistence across a restart.

### Contributions to the User Guide

* Documented [order approval](https://github.com/AY2627S1-CS2113-W14-4/tp/blob/master/docs/UserGuide.md#approving-an-order-approve), including the state change, command
  format, examples and errors ([#21](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/21)).
* Added the [item editing](https://github.com/AY2627S1-CS2113-W14-4/tp/blob/master/docs/UserGuide.md#editing-an-item-item-edit) and
  [CSV import](https://github.com/AY2627S1-CS2113-W14-4/tp/blob/master/docs/UserGuide.md#importing-items-from-csv-item-import) sections, with syntax, examples, validation
  rules and persistence notes ([#58](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/58),
  [#59](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/59)).
* Documented the inventory rename command and its effect on orders in the open
  [rename PR](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/60).

### Contributions to the Developer Guide

* Added the approval command's parsing, state transition and manual test cases
  ([#21](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/21)).
* Documented how item editing preserves counts and order snapshots, and how CSV import validates and previews
  all rows before saving. Added manual test steps for both features
  ([#58](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/58),
  [#59](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/59)).
* Documented inventory rename and its order-reference updates in the open
  [rename PR](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/60).

### Contributions to team-based tasks

* Tracked my features as issues [#11](https://github.com/AY2627S1-CS2113-W14-4/tp/issues/11),
  [#55](https://github.com/AY2627S1-CS2113-W14-4/tp/issues/55),
  [#56](https://github.com/AY2627S1-CS2113-W14-4/tp/issues/56) and
  [#57](https://github.com/AY2627S1-CS2113-W14-4/tp/issues/57), and updated each feature branch after related
  team changes merged.
* Added my entry to [About Us](https://github.com/AY2627S1-CS2113-W14-4/tp/blob/master/docs/AboutUs.md) in
  [#5](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/5).

### Review and mentoring contributions

* In [#41](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/41#discussion_r4357108511), identified that an
  invalid saved minimum could throw before validation and interrupt startup.
* In [#46](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/46#discussion_r4385187082), requested persistence
  for rejection reasons; I also flagged the removal of the
  [build workflow](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/46#discussion_r4385197984).
* In [#54](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/54#discussion_r4409678045), requested complete
  transfer instructions and examples in the User Guide, and a
  [TransferOrder class-diagram update](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/54#discussion_r4409705354).

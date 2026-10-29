# KKYTester - Project Portfolio Page

## Overview
StockHolm is a command-line app for managing several inventories from the keyboard. Stock enters and leaves an
inventory through import and export orders that are approved and delivered, so every change to stock is recorded.

I contributed to the  

### Summary of Contributions

#### Code contributed

[RepoSense Link](https://nus-cs2113-ay2627-s1.github.io/tp-dashboard/?search=kkytester&sort=groupTitle&sortWithin=title&timeframe=commit&mergegroup=&groupSelect=groupByRepos&breakdown=true&checkedFileTypes=docs~functional-code~test-code~other&since=2026-09-18T00%3A00%3A00&filteredFileName=&tabOpen=true&tabType=authorship&tabAuthor=KKYTester&tabRepo=AY2627S1-CS2113-W14-4%2Ftp%5Bmaster%5D&authorshipIsMergeGroup=false&authorshipFileTypes=docs~functional-code~test-code&authorshipIsBinaryFileTypeChecked=false&authorshipIsIgnoredFilesChecked=false)

#### Enhancements implemented

* **Import orders:** implemented the `Order` and `ImportOrder` models and the `import` command, supporting item names, optional types and decimal counts. Import orders start in `WAITING_APPROVAL` without changing inventory stock.
* **Export orders:** implemented `ExportOrder` and the `export` command to export a specified quantity or all remaining stock. The command rejects excessive quantities, removes exhausted items and records a separate item snapshot to preserve the order's original details.
* **Transfer orders:** implemented `transfer` with source and destination options, stock reservation, delivery to the destination and refunds on rejection or cancellation. Added transfer persistence, type-conflict checks and safe handling of inventory deletion, including refunds when a destination is deleted.
* **Order listing:** implemented `order list` to display numbered orders with their type, state and item details.
* **Order persistence:** implemented CSV saving and startup restoration, preserving order details and states across sessions. Added CSV quoting, validation, warnings for malformed rows and temporary-file replacement to protect existing data if writing fails.
* **Refactoring:** moved persistence handling from `Processor` into `Storage`, keeping business logic separate from file operations. Reused shared parsing helpers for name splitting and decimal validation to reduce duplication.
* **Testing and defensive code:** added JUnit tests for imports, exports, order listing, transfers, CSV validation and storage failures. Added assertions for export invariants and logging and assertions in `OrderStorage`.

#### Contributions to the UG

* No direct User Guide contributions are recorded in my authored commits so far.

#### Contributions to the DG

* Documented the import, export, transfer and order-listing commands, including their parsing, processing and effects on stock.
* Documented order storage, the CSV format, startup restoration and the persistence workflow after moving file operations into `Storage`.
* Created and refined the Order class diagram and order approval sequence diagram, with explanations of inheritance, encapsulation, state transitions and item snapshots.
* Added manual testing instructions for imports, exports, order listing, saving and restoring data, and transfers. Documented transfer endpoint defaults, stock reservation, delivery, refunds, persistence and inventory-deletion behaviour.

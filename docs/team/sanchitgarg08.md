# Sanchit Garg - Project Portfolio Page

## Overview

StockHolm is a command-line app for managing several inventories from the keyboard. Stock enters and leaves an
inventory through import and export orders that are approved and delivered, so every change to stock is recorded.

I built the features that tell users what they have and what they are running out of: viewing stock levels,
searching items across inventories, and per-item minimum stock levels with low-stock alerts and a restock list.

### Summary of Contributions

#### Code contributed

[RepoSense link](https://nus-cs2113-ay2627-s1.github.io/tp-dashboard/?search=sanchitgarg08&breakdown=true)

#### Enhancements implemented

* **Stock levels: `stock [NAME] [--below=COUNT]`** ([#16](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/16)):
  shows every item of an inventory with the total number of items and units, or only the items below a threshold.
  It works both inside and outside an inventory without changing where the user is, and keeps `item list` numbers
  so they can be used straight away with other commands. Totals are added with `BigDecimal`, so decimal counts add up
  exactly. While responding to review, I generalized the lookup into `requireNamedOrCurrentInventory`, which
  `restock` now reuses.
* **Item search: `find [KEYWORD] [--type=TYPE]`** ([#40](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/40)):
  finds items by part of their name and/or their exact type, ignoring case. Inside an inventory it searches that
  inventory; outside, it searches every inventory and groups the matches by inventory, so users can find an item
  without knowing where it is kept.
* **Minimum stock levels and low-stock alerts: `item min INDEX COUNT`, `restock [NAME]`**
  ([#41](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/41)):
  * Users set a minimum per item (`0` clears it). `Item.isLowOnStock()` and `getShortfall()` are the single
    definition of "low" and of the amount to reorder, and every feature below uses them.
  * `export` and `item min` add a `Low stock` alert with the amount to reorder, or an `Out of stock` alert when an
    item with a minimum runs out. `stock` marks low items `[LOW: min M]`, and `restock` lists everything that needs
    reordering.
  * *Highlight:* minimums are saved as an optional field in the existing JSON file. Save files from before the
    feature load and save unchanged, and an invalid minimum is ignored with a warning instead of losing the item.
  * Both #40 and #41 had to be rebuilt after the team's Parser/Processor refactor
    ([#36](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/36)). I resolved the conflicts with merge commits (not
    rebases), keeping the original commit history.
* **Defensive code** ([#30](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/30)): enabled assertions for
  `./gradlew run`, added assertions and `FINE`-level logging to the stock command and inventory lookups, and a test
  that checks an assertion fires. All later features follow the same pattern.
* **Tests:** about 60 JUnit tests across `ParserTest`, `ProcessorTest`, `ItemTest` and `InventoryStorageTest`,
  covering each command's success cases and every error message.

#### Contributions to the UG

* Wrote the sections for `stock`, `find`, `item min` and `restock`, the low-stock alert notes under `export`, and
  their rows in the *Command Summary*.
* Added the Quick Start notes and FAQ entries on running the jar from a terminal and from a write-protected folder.

#### Contributions to the DG

* Wrote *Item search: `find`* and *Low-stock thresholds and alerts: `item min`, `restock`*. Each covers how the
  feature is implemented, why, the alternatives considered and, for alerts, the limitations and possible extensions.
* Wrote the *Stock levels* design notes, the `requireNamedOrCurrentInventory` guard, and the assertion and logging
  conventions in *Testing and conventions*.
* UML diagrams: `FindSequence` (sequence), `LowStockClassDiagram` (class), `LowStockExportSequence` (sequence) and
  `LowStockAlertActivity` (activity).
* User stories for stock levels, search and minimum stock, and manual testing instructions for viewing stock
  levels, finding items, and minimum stock levels and restocking.

#### Contributions to team-based tasks

* Opened and tracked issues for my features: [#8](https://github.com/AY2627S1-CS2113-W14-4/tp/issues/8),
  [#29](https://github.com/AY2627S1-CS2113-W14-4/tp/issues/29),
  [#37](https://github.com/AY2627S1-CS2113-W14-4/tp/issues/37),
  [#38](https://github.com/AY2627S1-CS2113-W14-4/tp/issues/38),
  [#39](https://github.com/AY2627S1-CS2113-W14-4/tp/issues/39).
* Enabled assertions in `build.gradle` for the whole team ([#30](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/30)).
* Updated the User Guide's help example and FAQ for the v2.0 release.

#### Review/mentoring contributions

* TODO: add links to PRs you reviewed with non-trivial comments (e.g. #48).

#### Contributions beyond the project team

* TODO: add forum posts, or links to bugs reported in other teams' products (DG review, PE dry run).

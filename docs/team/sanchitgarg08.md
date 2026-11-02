# Sanchit Garg - Project Portfolio Page

## Overview

StockHolm is a command-line app for managing several inventories from the keyboard. Stock enters and leaves an
inventory through import, export and transfer orders that are approved and delivered, so every change to stock goes
through a recorded order.

I owned StockHolm's **stock visibility** features, which tell users what they have, where it is, and what they are
about to run out of. For v2.1 I also audited the whole UG and DG against the code.

### Summary of Contributions

#### Code contributed

[RepoSense link](https://nus-cs2113-ay2627-s1.github.io/tp-dashboard/?search=sanchitgarg08&sort=groupTitle&sortWithin=title&timeframe=commit&mergegroup=&groupSelect=groupByRepos&breakdown=true&checkedFileTypes=docs~functional-code~test-code~other&since=2026-09-18T00%3A00%3A00&filteredFileName=&tabOpen=true&tabType=authorship&tabAuthor=SanchitGarg08&tabRepo=AY2627S1-CS2113-W14-4%2Ftp%5Bmaster%5D&authorshipIsMergeGroup=false&authorshipFileTypes=docs~functional-code~test-code&authorshipIsBinaryFileTypeChecked=false&authorshipIsIgnoredFilesChecked=false)


#### Enhancements implemented

Each feature is complete end to end: parser, handler, saving where needed, an error message for every invalid input,
JUnit tests, a UG section, a DG design section with diagrams, and manual-test steps checked against the running app.

* **Minimum stock levels and low-stock alerts: `item min`, `restock`**
  ([#41](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/41), extended in v2.1). This is my largest feature.
  * *Depth:* users give each item a minimum. An `export` or `item min` that drops an item below it triggers an
    alert with the exact amount to reorder, or an out-of-stock warning. `stock` marks low items, and `restock` lists
    everything to reorder, across every inventory when used outside one.
  * *Design:* `Item.isLowOnStock()` and `getShortfall()` are the single definition of "low" and of the reorder
    amount. Four classes rely on it (`ItemHandlers`, `OrderHandlers`, `StockHandlers` and `ResultFormatter`), so
    alerts, markers and lists can never disagree.
  * *Hard parts:*
    1. It had to change shared code owned by teammates, such as the export flow and the JSON storage, without
       breaking their behaviour or tests.
    2. Saved data had to stay backward compatible. The minimum is an optional field, so files from earlier versions
       load and re-save unchanged, and an invalid value is ignored with a warning instead of losing the item.
    3. The feature had to be rebuilt twice as the codebase changed under it: after the Parser/Processor refactor
       ([#36](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/36)) and the reject/cancel order changes. I used
       merge commits so the history stayed accurate.
  * *Completeness:* it covers edge cases explicitly. An item exactly at its minimum is not low, `0` clears a
    minimum, an item that runs out loses its minimum with a clear alert, and decimal shortfalls are exact
    (`0.3 - 0.1 = 0.2`).
* **Item search: `find [KEYWORD] [--type=TYPE]`** ([#40](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/40)).
  * Searches by part of a name and/or an exact type, ignoring case and independent of the user's locale. Inside an
    inventory it searches only that one; outside, it searches all of them and groups the results. Matches keep their
    `item list` numbers, so users can act on them straight away.
* **Stock levels: `stock [NAME] [--below=COUNT]`** ([#16](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/16)).
  * Shows any inventory's items with exact `BigDecimal` totals, or only the items below a threshold, from anywhere.
    After review I extracted the guard `requireNamedOrCurrentInventory`, which `restock` later reused unchanged.
* **Defensive code** ([#30](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/30)): enabled assertions for the
  team's Gradle runs, added assertions and `FINE`-level logging, and documented the convention in the DG.
* **Testing:** 59 JUnit tests: 35 for command behaviour, 17 for parsing, 5 for `Item` and 2 for save-file
  compatibility. They cover every success path and error message of my commands.

#### Contributions to the UG

* Wrote the `stock`, `find`, `item min` and `restock` sections, the low-stock notes under `export`, and the Quick Start
  notes and FAQ on running the jar. For v2.1, checked every UG example against the app and fixed *Listing orders*.

#### Contributions to the DG

* Wrote *Item search: `find`* and *Low-stock thresholds and alerts*: implementation, design rationale, alternatives
  considered, limitations and extensions. Also wrote the *Stock levels* notes, the assertion and logging
  conventions, the related user stories and manual tests, the *Non-Functional Requirements* and the *Glossary*.
* UML diagrams created: `FindSequence` and `LowStockExportSequence` (sequence diagrams, with `alt`/`opt` frames),
  `LowStockClassDiagram` (class) and `LowStockAlertActivity` (activity). UML diagrams updated to match the code
  for v2.1: `CommandClassDiagram`, `ArchitectureDiagram`, `CouplingDiagram` and `LocationStateDiagram`.

#### Contributions to team-based tasks

* Maintained the team's user-story tracking spreadsheet, and coordinated the time of the team's first meeting.
* Updated the UG for v2.0 ([#52](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/52)); audited the UG, DG and
  website for v2.1. Opened and tracked issues for my features, e.g.
  [#37](https://github.com/AY2627S1-CS2113-W14-4/tp/issues/37)–
  [#39](https://github.com/AY2627S1-CS2113-W14-4/tp/issues/39).

#### Review/mentoring contributions

* Reviewed [#31](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/31) (advice on assertions and logging) and
  [#34](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/34) (documentation choices in the DG's order diagrams).

#### Contributions beyond the project team

* Reviewed team CS2113-T10-3's product, *BusyNotFree*, with technical comments on their project PR
  ([#17](https://github.com/NUS-CS2113-AY2627-S1/tp/pull/17)).

## Contributions to the Developer Guide (extracts)

**Low-stock alert on export** (sequence diagram):

![Sequence diagram for an export that triggers a low-stock alert](../images/LowStockExportSequence.png)

**Where the minimum stock level lives** (class diagram):

![Class diagram for minimum stock levels](../images/LowStockClassDiagram.png)

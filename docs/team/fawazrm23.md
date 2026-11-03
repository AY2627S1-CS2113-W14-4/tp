# Reyas Mohideen Fawaz - Project Portfolio Page

## Overview

StockHolm is a command-line application for managing stock across inventories. Orders track imports, exports and
transfers through approval and delivery; direct item commands support inventory maintenance. I worked on order
approval, inventory maintenance, data integrity, testing and technical documentation.

## Summary of Contributions

### Code contributed

[My attributed code on the tP Code Dashboard](https://nus-cs2113-ay2627-s1.github.io/tp-dashboard/?search=&sort=groupTitle&sortWithin=title&timeframe=commit&mergegroup=&groupSelect=groupByRepos&breakdown=true&checkedFileTypes=docs~functional-code~test-code~other&since=2026-09-18T00%3A00%3A00&filteredFileName=&tabOpen=true&tabType=authorship&tabAuthor=FawazRM23&tabRepo=AY2627S1-CS2113-W14-4%2Ftp%5Bmaster%5D&authorshipIsMergeGroup=false&authorshipFileTypes=docs~functional-code~test-code&authorshipIsBinaryFileTypeChecked=false&authorshipIsIgnoredFilesChecked=false)

### Enhancements implemented

* **Order approval** ([#21](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/21)):
  added `approve INDEX` and its parser, state transition and tests. Only orders awaiting approval move to
  `WAITING_FOR_DELIVERY`; invalid or repeated approvals leave the order and stock unchanged.
* **Item editing** ([#58](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/58)):
  added name and category edits that preserve count, minimum stock, list position and historical order snapshots.
  Integration tests cover duplicate names and persistence after a restart.
* **Bulk CSV import** ([#59](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/59)):
  built a UTF-8 CSV reader for quoted commas and doubled quotes, with row-specific validation errors. The command
  previews every merge before updating stock, so a malformed row, type conflict or overflow rejects the whole file.
* **Inventory rename** ([#60](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/60)):
  updated order source and destination references, including transfers and completed history, without replacing
  the inventory or its items. Validation prevents duplicate names and invalid transfer endpoints; tests cover
  quoted names and saved orders.
* **Input and stock safeguards** ([#78](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/78),
  [#79](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/79)):
  rejected extra text after commands that take no arguments, and prevented count overflow during item merges,
  CSV imports, transfers and order delivery. Tests verify that rejected operations do not partly change stock.
* **Order timestamps** ([#84](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/84)):
  recorded creation and last status-change times, displayed them in `order list`, and saved them in the order CSV.
  Loading older files keeps unknown times unknown; tests cover transitions, invalid dates and persistence.

### Contributions to the User Guide

* Wrote the [approval](https://github.com/AY2627S1-CS2113-W14-4/tp/blob/master/docs/UserGuide.md#approving-an-order-approve),
  [item editing](https://github.com/AY2627S1-CS2113-W14-4/tp/blob/master/docs/UserGuide.md#editing-an-item-item-edit),
  [CSV import](https://github.com/AY2627S1-CS2113-W14-4/tp/blob/master/docs/UserGuide.md#importing-items-from-csv-item-import) and
  [inventory rename](https://github.com/AY2627S1-CS2113-W14-4/tp/blob/master/docs/UserGuide.md#managing-inventories-inv) instructions, including formats, examples and
  error cases ([#21](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/21),
  [#58](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/58),
  [#59](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/59),
  [#60](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/60)).
* Explained overflow rejection and order timestamps, including the `Unknown` display for older saved orders
  ([#79](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/79),
  [#84](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/84)).
* Clarified when orders reserve or add stock and when direct item commands change stock, aligning the User Guide
  introduction with the README ([#89](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/89)).

### Contributions to the Developer Guide

* Explained approval, item editing, atomic CSV import, order-reference updates on rename, overflow guards and
  timestamp persistence, with manual test cases in the
  [Developer Guide](https://github.com/AY2627S1-CS2113-W14-4/tp/blob/master/docs/DeveloperGuide.md) ([#21](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/21),
  [#58](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/58),
  [#59](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/59),
  [#60](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/60),
  [#79](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/79),
  [#84](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/84)).
* Simplified the [architecture](../images/ArchitectureDiagram.png),
  [command class](../images/CommandClassDiagram.png),
  [package coupling](../images/CouplingDiagram.png) and
  [`inv add` sequence](../images/InvAddSequence.png) diagrams and their DG explanations; corrected two other diagram
  labels ([#86](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/86)).
* Added order and inventory user stories, corrected timestamp examples, and updated manual testing expectations
  ([#89](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/89)).

### Contributions to team-based tasks

* Completed assigned issues across v1.0, v2.0 and v2.1, from
  [approval](https://github.com/AY2627S1-CS2113-W14-4/tp/issues/11) and
  [CSV import](https://github.com/AY2627S1-CS2113-W14-4/tp/issues/57) to
  [timestamps](https://github.com/AY2627S1-CS2113-W14-4/tp/issues/50) and
  [diagram review](https://github.com/AY2627S1-CS2113-W14-4/tp/issues/85). I used separate feature branches,
  incorporated reviewer feedback and added my [About Us entry](https://github.com/AY2627S1-CS2113-W14-4/tp/blob/master/docs/AboutUs.md) ([#5](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/5)).

### Review and mentoring contributions

* Reviewed [teammates' PRs](https://github.com/AY2627S1-CS2113-W14-4/tp/pulls?q=is%3Apr+reviewed-by%3AFawazRM23+-author%3AFawazRM23): flagged a startup failure when loading an invalid minimum
  ([#41](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/41#discussion_r4357108511)); requested saved rejection
  reasons and retention of CI ([#46](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/46#discussion_r4385187082),
  [CI comment](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/46#discussion_r4385197984)); and requested clearer
  transfer instructions and a class-diagram update
  ([#54](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/54#discussion_r4409678045),
  [diagram comment](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/54#discussion_r4409705354)).
* In [#75](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/75#discussion_r4442091592), traced saved-name collisions to
  orders being attached to the wrong inventory. In [#91](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/91#discussion_r4450164343),
  identified missing and reversed steps in sequence diagrams; both reviews led to corrections.

### Contributions beyond the project team

* Reviewed [KeyboardWarrior's Developer Guide](https://github.com/NUS-CS2113-AY2627-S1/tp/pull/9), commenting on
  sequence order, activation notation, UML notes and a complexity explanation.

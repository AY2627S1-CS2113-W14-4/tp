# Nguyen Tan Kiet - Project Portfolio Page

## Overview

StockHolm is a command-line app for managing several inventories from the keyboard. Stock enters and leaves an
inventory through import and export orders that are approved and delivered, so every change to stock is recorded.

I set up the project's foundation: the command pipeline (Parser, Processor, UI), the inventory and item model, and
data saving with backups. Later I refactored the code so teammates could add commands without editing shared classes.

### Summary of Contributions

#### Code contributed

[RepoSense link](https://nus-cs2113-ay2627-s1.github.io/tp-dashboard/?search=yoosnk&breakdown=true)

#### Enhancements implemented

* **Core architecture** (PRs [#13](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/13),
  [#15](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/15)): replaced the Duke template with the StockHolm main loop
  and built the `Command` / `CommandType` / `Parser` / `Processor` / `Result` pipeline. The rest of the team built
  their features on this structure.
  * Commands carry their arguments in a key-value map (`--type=`, `--count=`), so a new option needs no new parsing code.
  * `AppState` tracks the current inventory, and the prompt shows its name. Each command declares where it can be used
    (inside an inventory, outside one, or anywhere), and the Processor checks this, not each handler.
* **Inventory and item commands**: `inv add/delete/list`, `enter`, `back`, and `item add/list/delete`. Adding an
  existing item merges the counts. Decimal counts use `BigDecimal` arithmetic, so `0.1 + 0.2` stores `0.3`.
  Infinite and invalid counts are rejected.
* **`help` command** ([#26](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/26)): generated from the `CommandType`
  enum, so the help text and error messages can never drift from the real syntax.
* **Inventory storage and backups** ([#27](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/27)): saves inventories as
  JSON with Gson and loads them on startup. Each command declares which save files it changes, and only those are
  saved.
  * *Highlight:* if a save file is corrupted or partly unreadable, both save files get a timestamped `.bak` copy before
    anything is overwritten, and identical backups are not duplicated. If a file cannot be read at all, startup stops
    instead of overwriting data that was not loaded. Invalid entries are skipped with one warning format for both files.
* **Large refactor** ([#36](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/36)): the Parser and Processor had grown
  as each feature was added. I split them into parsers and handlers per domain (inventory, item, order, stock, find).
  I moved usage text, location rules and save rules onto `CommandType`, and moved list formatting into
  `ResultFormatter`. This removed duplicated validation logic and left one place to change per feature. Adding a
  command now touches the enum, one parser and one handler.
* **Global order history** ([#48](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/48)): moved orders out of each
  inventory into one `OrderHistory`, so orders survive when their inventory is deleted and can be listed from anywhere.
  `reject`, `cancel` and `deliver` now check the order's state before changing stock, so a failed command does not
  change any data.
* **Code quality**: logging and assertions in `Inventory`
  ([#33](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/33)), and fixes before the v1.0 release
  ([#28](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/28)), e.g. splitting commands on any whitespace and
  enforcing order states inside `Order`.

#### Contributions to the UG

* Wrote the *Features* introduction and the command sections for `help`, `inv`, `enter`/`back`, `item add`,
  `item list`, `item delete`, `import`, `export`, `order list` and `bye`.
* Wrote *Saving the data*, including how timestamped backups work.

#### Contributions to the DG

* Wrote *Architecture*, *Coupling between packages*, *Flow of one command*, *Command*, *Parser*, *Processor*,
  *State*, *Inventory*, *UI*, *Inventory storage* and *Backups*, plus *Not implemented yet*, *Adding a new command*
  and *Testing and conventions*.
* UML diagrams: `ArchitectureDiagram`, `CommandClassDiagram`, `CouplingDiagram`, `InvAddSequence` and
  `LocationStateDiagram`.
* Manual testing instructions: launch and shutdown, saving and restoring data, sample data, inventory and item
  commands, other input, and running many commands at once.

#### Contributions to team-based tasks

* Set up the project skeleton ([#13](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/13)), including the Gson
  dependency and the StockHolm entry point.
* Improved the CI workflow ([#17](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/17)) with Gradle caching,
  concurrency limits that cancel outdated runs, and a timeout.
* Released [v1.0](https://github.com/AY2627S1-CS2113-W14-4/tp/releases/tag/v1.0).
* Removed the leftover Duke template text from the README and About Us.
* Opened issues to split work: [#6](https://github.com/AY2627S1-CS2113-W14-4/tp/issues/6),
  [#7](https://github.com/AY2627S1-CS2113-W14-4/tp/issues/7),
  [#23](https://github.com/AY2627S1-CS2113-W14-4/tp/issues/23),
  [#47](https://github.com/AY2627S1-CS2113-W14-4/tp/issues/47).

#### Review/mentoring contributions

* PRs reviewed, with non-trivial comments:
  [#16](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/16),
  [#19](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/19),
  [#22](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/22),
  [#24](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/24),
  [#30](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/30).
* Wrote the *Adding a new command* section of the DG so teammates could add features without reading the whole
  Processor.

#### Contributions beyond the project team

* TODO: add forum posts or links to bugs reported in other teams' products (PE dry run).

# Developer Guide

## Acknowledgements

* Project structure and build setup adapted from the [SE-EDU](https://se-education.org) project template.
* [Gson](https://github.com/google/gson): JSON library, added for the upcoming save/load feature (`Storage`).
* [JUnit 5](https://junit.org/junit5/): unit testing.
* [Checkstyle](https://checkstyle.org/): code style checks.
* [Gradle Shadow plugin](https://gradleup.com/shadow/): packages the app into a single runnable `stockholm.jar`.

## Design & implementation

### Architecture

![Architecture diagram](images/ArchitectureDiagram.png)

StockHolm is a command-line app built around one loop in `StockHolm.main`:

1. **Read**: `Ui.readInputFancy()` returns the line the user typed.
2. **Parse**: `Parser.parseCommand(line)` turns the text into a `Command`.
3. **Process**: `Processor.process(command, inventories)` runs the command and returns a `Result`.
4. **Print**: `Printer.printResult(result)` shows the result. The loop stops once `result.isExit()` is `true`.

Each step has one job, so the classes stay small and easy to test:

| Component | Job | Never does |
|---|---|---|
| `Parser` | Checks **syntax** (unknown command, missing argument) | Look at inventory data |
| `Processor` | Enforces **business rules** (e.g. no duplicate inventory names) | Read input, parse text, or print |
| `Printer` | Decides **how** output looks | Decide what happened |
| `StockHolm` | **Owns the data** (`ArrayList<Inventory>`) and passes it to the Processor | — |

`Parser` and `Processor` hold no state of their own; all their methods are `static`.

Any user-facing error is thrown as a `StockHolmException`, from either the Parser or the Processor.
The main loop catches it in a single place and prints its message, so you never need to print errors yourself.

### Flow of one command

The sequence diagram below shows `inv add Shop`, both when it succeeds and when the inventory already exists.

![Sequence diagram for inv add Shop](images/InvAddSequence.png)

### Command component

Package `stockholm.command`: `Command`, `CommandType`, `ArgKey`.

![Command class diagram](images/CommandClassDiagram.png)

A `Command` is an **op code** plus **named arguments**:

* `CommandType` is the op code: which action to run (e.g. `INV_ADD`).
* `ArgKey` names an argument (e.g. `INV_NAME`). Using an enum instead of strings means a mistyped key is a compile error.
* `args` is a `Map<ArgKey, String>` holding the arguments.

```java
// Command with arguments
Command add = new Command(CommandType.INV_ADD, Map.of(ArgKey.INV_NAME, "Shop"));
// Command without arguments
Command quit = new Command(CommandType.QUIT);

add.getArg(ArgKey.INV_NAME);   // "Shop"
add.hasArg(ArgKey.INV_NAME);   // true
quit.hasArg(ArgKey.INV_NAME);  // false
quit.getArg(ArgKey.INV_NAME);  // throws IllegalStateException
```

* Use `getArg` for **required** arguments. The Parser guarantees they are present, so a missing one is a bug, and
  `getArg` throws `IllegalStateException` to make it obvious.
* Use `hasArg` to check **optional** arguments before reading them.
* `Command` is immutable: it is a `record`, and its map is copied on construction, so it cannot change afterwards.

Rules for arguments:

* **Values are strings.** The Parser checks their syntax (e.g. that a count is a number), and the Processor converts
  them when it uses them, e.g. `Integer.parseInt(command.getArg(ArgKey.COUNT))`.
* **Commands carry names, not objects.** To refer to an existing inventory or item, put its name in the command; the
  Processor looks up the object (see `Processor.findInventory`). The Parser has no access to the data, so it cannot
  supply the real object.

### Parser component

`stockholm.parser.Parser` has one public method, `parseCommand(String rawInput)`.

* It splits the input into the command word and the rest (`splitFirstWord`), then `switch`es on the command word.
  Commands with subcommands get their own helper, e.g. `parseInvCommand` for `inv add|delete|list`.
* Blank input becomes `NO_OP`, so pressing Enter does nothing.
* **Aliases** live in the `ALIASES` map (e.g. `bye`, `exit`, `close` → `quit`). Add new aliases there and nowhere else.
* `requireName(name, usage)` throws a `StockHolmException` with a usage hint if an inventory name is empty.
  Its message always says "Missing inventory name", so for other arguments, generalize it (e.g. pass in the
  argument's label) or add a similar check.

### Processor component

`stockholm.processor.Processor` has one public method, `process(Command command, ArrayList<Inventory> inventories)`,
which `switch`es on `command.type()` and calls one private handler per command:

```java
case INV_ADD:
    return addInventory(command.getArg(ArgKey.INV_NAME), inventories);
```

* A handler either returns a `Result`, or throws a `StockHolmException` if a business rule is broken
  (e.g. `"Inventory already exists: Shop"`).
* `Result(String message, boolean isExit)` is a `record`. Use an empty message to print nothing,
  and `isExit = true` only for `QUIT`.
* Command types the Processor does not handle yet fall through to `default` and throw
  `"Command not supported yet: ..."`.

### UI component

Package `stockholm.ui`:

* `InputReader.readLine()` reads one raw line from standard input. It returns `"quit"` when input ends
  (e.g. Ctrl+D), so the app always exits cleanly.
* `Printer` is the **only** class that writes to the screen:
    * `printResult(Result)` prints the message, or nothing if it is empty.
    * `printIndent(String)` prints indented text; every line of a multi-line message is indented.
    * `printIndentedError(String)` does the same, but writes to standard error.
    * `printBar()` prints a horizontal line as wide as the terminal (`$COLUMNS`), or 100 characters if that is unknown.
    * `printPrompt()` prints `❯ `; `printPrompt(String)` prints a prompt with an inventory name.
* `Ui` combines `Printer` and `InputReader` calls for common screens:
  `readInputFancy()` (the prompt between two bars), `printStarter()`, and `printEnding()`.

### Inventory component

Package `stockholm.inventory`:

* `Inventory(String name)` is a named list of items. `getName()` is also how inventories are looked up.
  `addItem(Item)` adds an item; there is no way to read the items back yet.
* `Item` currently only has the fields `name` and `count`.

### Not implemented yet

These exist as placeholders, so avoid depending on their current shape:

* `CommandType.ENTER` and `CommandType.BACK`: the Parser never creates them, and the Processor throws "not supported yet".
* `storage.Storage`: will save data to `./data/stockholm.json` using Gson.
* Package `order` (`Order`, `OrderState`, `ImportOrder`, `ExportOrder`, `TransferOrder`).

### Adding a new command

Example: a hypothetical `item add INVENTORY ITEM` command that adds an item to an inventory.

1. **Op code**: add `ITEM_ADD` to `CommandType`, with a Javadoc comment showing its syntax.
2. **Argument keys**: add any new keys to `ArgKey`, e.g. `ITEM_NAME`. Reuse existing keys such as `INV_NAME`
   where they mean the same thing.
3. **Parse it** in `Parser`: add a `case` (here `"item"`, with a `parseItemCommand` helper like `parseInvCommand`),
   check the syntax (throw a `StockHolmException` with a usage hint if an argument is missing), and build the command:
   ```java
   return new Command(CommandType.ITEM_ADD, Map.of(
           ArgKey.INV_NAME, inventoryName,
           ArgKey.ITEM_NAME, itemName));
   ```
4. **Process it** in `Processor`: add a `case ITEM_ADD` that calls a new private handler, e.g.
   `addItem(command.getArg(ArgKey.INV_NAME), command.getArg(ArgKey.ITEM_NAME), inventories)`. The handler looks up the
   inventory by name, throws a `StockHolmException` if it does not exist, and returns a `Result` with the message to show.
5. **Test it**: add cases to `ParserTest` (valid input and each syntax error) and `ProcessorTest`
   (success and each broken rule). Small helpers such as `invCommand(type, name)` in those tests keep long
   `new Command(...)` calls readable.
6. **Document it** in the User Guide.

You never need to touch `StockHolm`, `Printer` or `Result` to add a command.

### Testing and conventions

* Use **Java 25**. If your default JDK is different, run `export JAVA_HOME=/path/to/jdk-25` first.
* Run all tests and style checks with `./gradlew test checkstyleMain checkstyleTest`.
* Test classes mirror the main package structure, e.g. `src/test/java/stockholm/parser/ParserTest.java`.
* Test method names follow `methodName_condition_expectedResult`, e.g. `parseCommand_invAddWithoutName_throwsException`.
* To test console output, redirect `System.out` to a `ByteArrayOutputStream` in `@BeforeEach` and restore it in
  `@AfterEach` (see `PrinterTest`).
* Diagram sources are in `docs/diagrams/*.puml`. After editing one, regenerate the PNG with
  `java -jar plantuml.jar -tpng -o ../images docs/diagrams/*.puml`
  ([download plantuml.jar](https://plantuml.com/download)).


## Product scope
### Target user profile

People who need to manage multiple inventories and keep track of the history of changes to them.

### Value proposition

StockHolm lets users manage multiple inventories from a fast, keyboard-driven CLI, 
with an approval-based order workflow that keeps stock accurate and a full audit trail of every change.

## User Stories

|Version| As a ... | I want to ... | So that I can ...|
|--------|----------|---------------|------------------|
|v1.0|new user|see usage instructions|refer to them when I forget how to use the application|
|v2.0|user|find a to-do item by name|locate a to-do without having to go through the entire list|

## Non-Functional Requirements

{Give non-functional requirements}

## Glossary

* *glossary item* - Definition

## Instructions for manual testing

{Give instructions on how to do a manual product testing e.g., how to load sample data to be used for testing}
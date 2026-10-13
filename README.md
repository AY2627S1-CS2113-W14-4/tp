# StockHolm

StockHolm is a command-line app for managing several inventories from the keyboard. Stock enters and leaves an
inventory through import and export orders that are approved and delivered, so every change to stock is recorded.

* [User Guide](docs/UserGuide.md)
* [Developer Guide](docs/DeveloperGuide.md)
* [About Us](docs/AboutUs.md)
* [Download the latest release](https://github.com/AY2627S1-CS2113-W14-4/tp/releases)

## Setting up in IntelliJ

Prerequisites: JDK 25 (use the exact version), update IntelliJ to the most recent version.

1. **Ensure IntelliJ JDK 25 is defined as an SDK**, as described [here](https://www.jetbrains.com/help/idea/sdk.html#set-up-jdk) -- this step is not needed if you have used JDK 25 in a previous IntelliJ project.
1. **Import the project _as a Gradle project_**, as described [here](https://se-education.org/guides/tutorials/intellijImportGradleProject.html).
1. **Verify the setup**: locate `src/main/java/stockholm/StockHolm.java`, right-click it, and choose `Run StockHolm.main()`.
   If the setup is correct, you should see:
   ```
   Hello, Welcome to StockHolm, your inventory manager program
       Loaded 0 inventory(ies) and 0 order(s).
   ```
   followed by a bar and the `❯` prompt. Type `bye` to exit.

**Warning:** Keep the `src\main\java` folder as the root folder for Java files (i.e., don't rename those folders or move Java files to another folder outside of this folder path), as this is the default location some tools (e.g., Gradle) expect to find Java files.

## Build automation using Gradle

* This project uses Gradle for build automation and dependency management (see `build.gradle`).
* `./gradlew shadowJar` builds the runnable `build/libs/stockholm.jar`; run it with `java -jar build/libs/stockholm.jar`.
* If you are new to Gradle, refer to the [Gradle Tutorial at se-education.org/guides](https://se-education.org/guides/tutorials/gradle.html).

## Testing

* JUnit tests are in `src/test/java`, mirroring the main package structure. Run them with `./gradlew test`.
* If you are new to JUnit, refer to the [JUnit Tutorial at se-education.org/guides](https://se-education.org/guides/tutorials/junit.html).

## Checkstyle

* The Checkstyle rules are in `config/checkstyle`. Run them with `./gradlew checkstyleMain checkstyleTest`.
* If you are new to Checkstyle, refer to the [Checkstyle Tutorial at se-education.org/guides](https://se-education.org/guides/tutorials/checkstyle.html).

## CI using GitHub Actions

The project uses [GitHub actions](https://github.com/features/actions) for CI. When you push a commit to this repo or PR against it, GitHub actions will run automatically to build and verify the code as updated by the commit/PR.

# Advik Nagpal - Project Portfolio Page

## Overview

StockHolm is a command-line application for managing stock across inventories from the keyboard. Import, export, and transfer orders record and track goods movement through an approval and delivery workflow, keeping inventory levels accurate with a full audit log.

I implemented the order lifecycle completion features (order delivery, rejection with reasons, and order cancellation with stock refunds), CSV persistence for rejection reasons, and advanced order management (paginated order history display and multi-criteria order filtering). I also wrote the corresponding User Guide and Developer Guide documentation and manual test cases.

### Summary of Contributions

#### Code contributed

[RepoSense link](https://nus-cs2113-ay2627-s1.github.io/tp-dashboard/?search=adviknagpal15&breakdown=true)

#### Enhancements implemented

* **Order delivery: `deliver INDEX`** ([#22](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/22)):
  * Transitions an order from `WAITING_FOR_DELIVERY` to `DELIVERED`.
  * For an import order, delivery adjusts inventory stock by adding or merging the items using exact `BigDecimal` arithmetic. For an export order, stock was already reserved and deducted upon order creation, so delivery finalizes the order without duplicate deduction.
  * Rejects invalid order numbers, double delivery, and orders not in the `WAITING_FOR_DELIVERY` state.
* **Order rejection with reason: `reject INDEX REASON`** ([#46](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/46)):
  * Rejects an order in `WAITING_APPROVAL`, transitioning it to `REJECTED` and attaching a mandatory non-blank explanatory reason.
  * *Stock refund highlight:* When an export order is rejected, the reserved stock is automatically refunded and merged back into the inventory's stock. Import order rejections leave inventory stock untouched.
  * Updated CSV persistence in `OrderStorage` to serialize and restore rejection reasons across application restarts, with full support for commas and quotes.
* **Order cancellation: `cancel INDEX`** ([#46](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/46)):
  * Cancels active orders in `WAITING_APPROVAL` or `WAITING_FOR_DELIVERY`, moving them to `CANCELED`.
  * Safely refunds reserved stock for export orders back to the inventory, while protecting completed (`DELIVERED`), `REJECTED`, or already `CANCELED` orders from invalid transitions.
* **Paginated order history: `order list [PAGE] [--size=SIZE]`** ([#64](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/64)):
  * Prevents terminal clutter by paginating order history (defaulting to 10 orders per page), supporting custom page sizes via `--size=SIZE` (or `--limit=SIZE`).
  * Implemented overflow-safe total page count calculation: `(total + size - 1) / size`.
  * *Architectural highlight:* Created the `IndexedOrder` record pairing each order with its permanent 1-based index in the global history. This cleanly decoupled `OrderHandlers` from `ResultFormatter` while guaranteeing that paginated views display the global order index, allowing users to reference displayed orders directly in `approve`, `reject`, `cancel`, or `deliver` regardless of page.
* **Multi-criteria order filtering: `order list [--status=STATUS] [--inv=NAME] [--type=TYPE]`** ([#64](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/64), [#66](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/66)):
  * Enables filtering order history by status (`WAITING_APPROVAL`, `WAITING_FOR_DELIVERY`, `DELIVERED`, `REJECTED`, `CANCELED`), inventory name (source or destination), and order type (`import`, `export`, `transfer`).
  * Supports seamless combination of multiple filters alongside pagination (e.g. `order list 1 --size=5 --status=WAITING_APPROVAL --inv=Shop`), with case-insensitive argument parsing and helpful empty-state feedback (`No orders yet.` vs. `No matching orders found.`).
* **Defensive code & testing:**
  * Authored over 100 JUnit test cases across `OrderTest`, `OrderStorageTest`, `StorageTest`, `ParserTest`, `ProcessorTest`, and `ResultFormatterTest`.
  * Covered state machine transitions, defensive argument parsing, export refund stock calculations, CSV round-tripping of reasons, pagination boundaries, and filter permutations.

#### Contributions to the UG

* Documented the `deliver`, `reject`, and `cancel` commands with syntax, arguments, execution examples, error handling, and state transition explanations.
* Documented `order list` pagination and filtering options (`PAGE`, `--size`, `--status`, `--inv`, `--type`), including practical multi-filter examples.
* Updated the *Command Summary* table with rows and syntax for `deliver`, `reject`, `cancel`, and the enhanced `order list`.

#### Contributions to the DG

* Documented implementation details and state transitions for order delivery, rejection, and cancellation in the Order component section.
* Documented the `Order` state encapsulation and stock refund mechanism for canceled/rejected export orders.
* Documented the CSV persistence update in `OrderStorage` for storing rejection reasons.
* Documented the design and decoupling of `order list` pagination and filtering using `IndexedOrder`.
* Added user stories for delivering orders, viewing paginated order lists, and filtering order history.
* Wrote extensive manual testing instructions for:
  * *Delivering orders*: import item creation/merging, export completion, invalid order numbers, duplicate delivery rejection.
  * *Rejecting orders*: reason validation, export stock refunds, state error checks, behavior on deleted inventories.
  * *Canceling orders*: cancellation from approval/delivery stages, export stock refunds, terminal state rejection.
  * *Listing orders*: pagination navigation, page size bounds, filtering by status/inventory/type, and combined filters.

#### Contributions to team-based tasks

* Reviewed and merged teammates' PRs, including Fawaz's AboutUs PR ([#5](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/5)) and Kiet's global order history refactoring ([#48](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/48)).
* Maintained CI/CD stability: restored and maintained the GitHub Actions workflow (`.github/workflows/gradle.yml`) after accidental removal.
* Maintained repository cleanliness: removed residual IDE workspace configuration files (`tp/.idea/workspace.xml`).
* Authored and updated team member profile information in `AboutUs.md`.

#### Review/mentoring contributions

* Actively reviewed and integrated team pull requests, ensuring compatibility with order handling and storage workflows.
* Addressed peer code review feedback on PR [#46](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/46), adding persistent CSV storage for rejection reasons and restoring the CI workflow.
* PRs reviewed with non-trivial comments:
  * [#48](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/48): reviewed migration of orders into global `OrderHistory` to ensure compatibility with order lifecycle transitions and list formatting.

#### Contributions beyond the project team

* TODO: add forum posts or links to bugs reported in other teams' products (PE dry run).

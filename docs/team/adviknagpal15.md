# Advik Nagpal - Project Portfolio Page

## Overview

StockHolm is a command-line application for managing stock across inventories from the keyboard. Import, export, and
transfer orders record and track goods movement through an approval and delivery workflow, keeping inventory levels
accurate with a full audit log.

I implemented the order lifecycle completion features (order delivery, rejection with reasons, and order cancellation
with stock refunds), CSV persistence for rejection reasons, and advanced order management (paginated order history
display and multi-criteria order filtering). I also authored the corresponding User Guide and Developer Guide sections,
diagram references, and manual test cases.

## Summary of Contributions

### Code contributed

[My attributed code on the tP Code Dashboard](https://nus-cs2113-ay2627-s1.github.io/tp-dashboard/?search=adviknagpal15&sort=groupTitle&sortWithin=title&timeframe=commit&mergegroup=&groupSelect=groupByRepos&breakdown=true&checkedFileTypes=docs~functional-code~test-code~other&since=2026-09-18T00%3A00%3A00&filteredFileName=&tabOpen=true&tabType=authorship&tabAuthor=adviknagpal15&tabRepo=AY2627S1-CS2113-W14-4%2Ftp%5Bmaster%5D&authorshipIsMergeGroup=false&authorshipFileTypes=docs~functional-code~test-code&authorshipIsBinaryFileTypeChecked=false&authorshipIsIgnoredFilesChecked=false)

### Enhancements implemented

* **Order delivery: `deliver INDEX`** ([#22](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/22)):
  * Transitions an order from `WAITING_FOR_DELIVERY` to `DELIVERED` using guarded state checks (`checkCanDeliver()`).
  * For an import order, delivery adjusts inventory stock by adding or merging the items using exact `BigDecimal`
    arithmetic via `addOrMerge()`. For an export order, stock was already reserved and deducted upon order creation, so
    delivery finalizes the order without duplicate deduction. For transfer orders, delivery adds or merges the items into
    the destination inventory.
  * Rejects invalid order numbers, double delivery, and orders not in the `WAITING_FOR_DELIVERY` state. Ensures the
    receiving inventory exists before modifying stock.
* **Order rejection with explanatory reason: `reject INDEX REASON`** ([#46](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/46)):
  * Rejects an order in `WAITING_APPROVAL`, transitioning it to `REJECTED` and attaching a mandatory non-blank reason.
    Supports unquoted text, quoted phrases (`"Damaged packaging"`), or `--reason=REASON`.
  * *Stock refund highlight:* When an export or transfer order is rejected, the reserved stock is automatically
    refunded and merged back into the source inventory's stock. Import order rejections leave inventory stock untouched.
  * Updated CSV persistence in `OrderStorage` to serialize and restore rejection reasons across application restarts,
    handling quoted commas and doubled quotes while preserving backward compatibility with earlier CSV formats.
* **Order cancellation: `cancel INDEX`** ([#46](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/46)):
  * Cancels active orders in `WAITING_FOR_DELIVERY`, moving them to `CANCELED`. Orders still in `WAITING_APPROVAL` are
    guided to `reject` instead, while completed (`DELIVERED`), `REJECTED`, or already `CANCELED` orders are prevented
    from invalid state transitions.
  * Safely refunds reserved stock for export and transfer orders back to the source inventory.
  * Shares atomic state-transition validation with `OrderHandlers.changeState`, ensuring inventory and stock prerequisites
    succeed before modifying order records or stock counts.
* **Paginated order history: `order list [PAGE] [--size=SIZE]`** ([#64](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/64)):
  * Prevents terminal clutter by paginating order history (defaulting to 10 orders per page), supporting custom page sizes
    via `--size=SIZE` (or `--limit=SIZE`).
  * Implemented overflow-safe total page count calculation: `(total + size - 1) / size`.
  * Defensively handles page boundaries and invalid page requests, providing clear navigation feedback (`No page number P. There are N page(s); use order list to see the first page.`).
  * *Architectural highlight:* Created the `IndexedOrder` record pairing each order with its permanent 1-based index in
    the global history. This cleanly decoupled `OrderHandlers` from `ResultFormatter` while guaranteeing that paginated views
    display the true global order index, allowing users to reference displayed orders directly in `approve`, `reject`,
    `cancel`, or `deliver` regardless of page.
* **Multi-criteria order filtering: `order list [--status=STATUS] [--inv=NAME] [--type=TYPE]`** ([#64](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/64), [#66](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/66)):
  * Enables filtering order history by status (`WAITING_APPROVAL`, `WAITING_FOR_DELIVERY`, `DELIVERED`, `REJECTED`, `CANCELED`),
    inventory name (matching either source or destination case-sensitively), and order type (`import`, `export`, `transfer`).
  * Supports filter aliases (`--state`, `--inventory`, `--kind`, `--limit`) and seamless combination of multiple filters
    alongside custom page sizes and page numbers (e.g. `order list 1 --size=5 --status=WAITING_APPROVAL --inv=Shop`).
  * Distinguishes between an empty global order history (`No orders yet.`) and a filter that matches no orders (`No matching orders found.`).
* **Defensive code & testing:**
  * Authored over 100 JUnit test cases across `OrderTest`, `OrderStorageTest`, `StorageTest`, `ParserTest`, `ProcessorTest`,
    and `ResultFormatterTest`.
  * Covered state machine transitions, defensive argument parsing, export refund stock calculations, CSV round-tripping of
    reasons, pagination boundaries, and filter permutations.

### Contributions to the User Guide

* Documented the [order list](https://github.com/AY2627S1-CS2113-W14-4/tp/blob/master/docs/UserGuide.md#listing-orders-order-list)
  command with pagination (`PAGE`, `--size`) and multi-criteria filtering (`--status`, `--inv`, `--type`), including practical
  multi-filter examples and expected output ([#64](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/64),
  [#66](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/66)).
* Documented the [reject](https://github.com/AY2627S1-CS2113-W14-4/tp/blob/master/docs/UserGuide.md#rejecting-an-order-reject),
  [cancel](https://github.com/AY2627S1-CS2113-W14-4/tp/blob/master/docs/UserGuide.md#canceling-an-order-cancel), and
  [deliver](https://github.com/AY2627S1-CS2113-W14-4/tp/blob/master/docs/UserGuide.md#delivering-an-order-deliver)
  commands with syntax, argument formats, stock effects, execution examples, and error handling
  ([#22](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/22), [#46](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/46)).
* Updated the [Command Summary](https://github.com/AY2627S1-CS2113-W14-4/tp/blob/master/docs/UserGuide.md#command-summary)
  table with rows, links, and syntax for `deliver`, `reject`, `cancel`, and the enhanced `order list`
  ([#22](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/22), [#46](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/46),
  [#66](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/66)).

### Contributions to the Developer Guide

* Documented implementation details, encapsulation, and lifecycle state transitions for order delivery, rejection, and
  cancellation in the [Order component](https://github.com/AY2627S1-CS2113-W14-4/tp/blob/master/docs/DeveloperGuide.md#order-component)
  section ([#22](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/22), [#46](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/46)).
* Documented the `Order` state encapsulation and automatic stock refund mechanism for canceled and rejected export/transfer
  orders, including the shared `OrderHandlers.changeState` validation flow ([#46](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/46)).
* Documented the CSV persistence update in [Order storage component](https://github.com/AY2627S1-CS2113-W14-4/tp/blob/master/docs/DeveloperGuide.md#order-storage-component)
  for serializing and restoring rejection reasons across restarts ([#46](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/46)).
* Documented the design and decoupling of `order list` pagination and multi-criteria filtering using the `IndexedOrder`
  record ([#64](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/64)).
* Added user stories for delivering orders, canceling orders, rejecting orders, viewing paginated order lists, and filtering
  order history ([#22](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/22), [#46](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/46),
  [#64](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/64)).
* Authored comprehensive manual testing instructions for:
  * [Delivering orders](https://github.com/AY2627S1-CS2113-W14-4/tp/blob/master/docs/DeveloperGuide.md#delivering-orders):
    import item creation/merging, export completion without double deduction, invalid order numbers, duplicate delivery rejection.
  * [Rejecting orders](https://github.com/AY2627S1-CS2113-W14-4/tp/blob/master/docs/DeveloperGuide.md#rejecting-orders):
    reason validation, export stock refunds, state error checks, behavior on deleted inventories.
  * [Canceling orders](https://github.com/AY2627S1-CS2113-W14-4/tp/blob/master/docs/DeveloperGuide.md#canceling-orders):
    cancellation from delivery stages, export stock refunds, terminal state rejection.
  * [Listing orders](https://github.com/AY2627S1-CS2113-W14-4/tp/blob/master/docs/DeveloperGuide.md#listing-orders):
    pagination navigation, page size bounds, filtering by status/inventory/type, and combined filters.

### Contributions to team-based tasks

* Maintained CI/CD stability: restored and maintained the GitHub Actions workflow (`.github/workflows/gradle.yml`) after
  accidental removal, ensuring continuous automated testing ([#46](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/46)).
* Maintained repository cleanliness: removed residual IDE workspace configuration files (`tp/.idea/workspace.xml`,
  commit [25cd641](https://github.com/AY2627S1-CS2113-W14-4/tp/commit/25cd641)).
* Authored and updated team member profile information in `AboutUs.md` ([#4](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/4)).
* Reviewed and merged teammates' pull requests, including Fawaz's AboutUs PR ([#5](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/5))
  and Kiet's global order history refactoring ([#48](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/48)).

### Review and mentoring contributions

* Actively reviewed and integrated team pull requests, ensuring compatibility with order lifecycle handling and storage workflows.
* PRs reviewed:
  * [#48](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/48): reviewed migration of orders into global `OrderHistory` to
    verify compatibility with order lifecycle transitions, state guards, and list formatting.
* Addressed peer code review feedback:
  * In [#46](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/46), implemented Fawaz's feedback by adding persistent CSV storage
    for rejection reasons (with quote/comma escaping) and restoring the CI workflow.
  * In [#64](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/64), addressed review feedback by fixing integer overflow in
    pagination total calculation, aligning case-sensitive inventory matching with inventory lookup, and committing `IndexedOrder`.

### Contributions beyond the project team

* Reviewed the Developer Guide of team [CS2113-T11-3 (Bob)](https://github.com/NUS-CS2113-AY2627-S1/tp/pull/18):
  * Flagged missing design elements: [identified that `RequestManager` and `Request` were missing from the Design diagram and Model component list, and requests had no user stories, glossary entry, or manual test steps](https://github.com/NUS-CS2113-AY2627-S1/tp/pull/18#discussion_r4397623551).
  * Flagged input validation gaps: [queried whether international phone numbers with a `+` prefix were handled properly](https://github.com/NUS-CS2113-AY2627-S1/tp/pull/18#discussion_r4397663757).
  * Flagged architectural component coupling: [noted an architectural inconsistency where `ContactManager` printed its own confirmation instead of delegating rendering to `Ui`](https://github.com/NUS-CS2113-AY2627-S1/tp/pull/18#discussion_r4397727651).
  * Flagged user story / test contradictions: [pointed out contradictory preconditions in the "Prepare a lab session" manual testing instructions](https://github.com/NUS-CS2113-AY2627-S1/tp/pull/18#discussion_r4397767835).

## User Guide extracts

### Listing orders: `order list`

Lists orders across all inventories in the order they were created, with the inventory each one is for,
its state, and its creation and most recent status-change times in UTC. The list is paginated (10 orders per page
by default) to keep long order histories readable.
Works both inside and outside an inventory. Orders of a deleted inventory are still listed.

Format: `order list [PAGE] [--status=STATUS] [--inv=NAME] [--type=TYPE] [--size=SIZE]`

* `PAGE` (or `--page=PAGE`): optional one-based page number (defaults to `1`).
* `--status=STATUS` (or `--state=STATE`): filter by order status (`WAITING_APPROVAL`, `WAITING_FOR_DELIVERY`,
  `DELIVERED`, `REJECTED`, or `CANCELED`, matched case-insensitively).
* `--inv=NAME` (or `--inventory=NAME`): filter by inventory name (matches case-sensitively if it is either the
  source or destination).
* `--type=TYPE` (or `--kind=KIND`): filter by order type (`import`, `export`, or `transfer`, matched case-insensitively).
* `--size=SIZE` (or `--limit=SIZE`): optional number of orders per page (defaults to `10`).
* Order numbers are global positions in the order history, so they can be referenced in `approve`, `reject`,
  `cancel`, and `deliver` commands regardless of which page they appear on.
* With no orders at all, it shows `No orders yet.`; if no order matches the filters, `No matching orders found.`

Example output:

```text
Orders (Page 1 of 1):
1. Import order (to Shop)
   State: WAITING_APPROVAL
   Created (UTC): 2026-11-02T06:00:00Z
   Status changed (UTC): 2026-11-02T06:00:00Z
   Item name: Rice
   Item type: Food
   Item count: 2.5
2. Export order (from Warehouse)
   State: WAITING_FOR_DELIVERY
   Created (UTC): 2026-11-02T06:05:00Z
   Status changed (UTC): 2026-11-02T06:07:00Z
   Item name: Pen
   Item type:
   Item count: 3
```

[Original contribution in PR #64](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/64/files) ·
[Current User Guide](../UserGuide.md#listing-orders-order-list)

### Rejecting an order: `reject`

Rejects an import, export or transfer order awaiting approval, with a reason.
Use `order list` to find its number.
Only an order in `WAITING_APPROVAL` can be rejected. Its state then becomes `REJECTED`.
For an import order, rejection records that the order is rejected; stock is unchanged.
For an export or transfer order, rejection returns the deducted stock to the inventory it came from.

Format: `reject INDEX REASON`

* Works both inside and outside an inventory.
* `INDEX` is the one-based number displayed by `order list`.
* `REASON` is the explanation for rejecting the order, e.g. `Damaged packaging` or `Out of stock`.
  It may be wrapped in quotes (`"Damaged packaging"`) or given as `--reason="Damaged packaging"`.

[Original contribution in PR #46](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/46/files) ·
[Current User Guide](../UserGuide.md#rejecting-an-order-reject)

### Delivering an order: `deliver`

Marks a waiting-for-delivery import, export or transfer order as delivered.
Use `order list` to find its number.
Only an order in `WAITING_FOR_DELIVERY` can be delivered. Its state then becomes `DELIVERED`.
For an import order, delivery adds the ordered goods to the stock of the inventory it is for (creating a new
item or merging into an existing item with the same name).
For an export order, delivery records that the goods have been delivered; stock was already reduced when
the export order was created.
For a transfer order, delivery adds or merges the ordered goods into the destination inventory.

Format: `deliver INDEX`

[Original contribution in PR #22](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/22/files) ·
[Current User Guide](../UserGuide.md#delivering-an-order-deliver)

## Developer Guide extracts

### Order lifecycle and state transitions

* **Encapsulation and state transitions:** `Order` keeps its state private and exposes `approve()`, `reject(reason)`,
  `cancel()` and `deliver()` to enforce `WAITING_APPROVAL` -> `WAITING_FOR_DELIVERY` -> `DELIVERED`,
  `WAITING_APPROVAL` -> `REJECTED`, and `WAITING_FOR_DELIVERY` -> `CANCELED`, throwing `StockHolmException` for an
  invalid transition. This uses an enum and guarded methods, rather than the State design pattern with separate state classes.
* Each transition has a matching `checkCanReject(reason)`, `checkCanCancel()` or `checkCanDeliver()` that throws the
  same exception without changing anything, so the Processor can validate a transition before touching stock.
* `reject`, `cancel` and `deliver` share `OrderHandlers.changeState`, which runs the order's `checkCanX` first, then,
  only if the change adds stock, looks up the receiving endpoint's inventory and checks `requireCanAddOrMerge`,
  and only then changes the state and merges a copy of the item. So a failed command changes nothing, and a state
  problem is reported ahead of `Inventory NAME no longer exists.` or an invalid stock merge.

[Original contribution in PR #22](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/22/files),
[PR #46](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/46/files) ·
[Current Developer Guide](../DeveloperGuide.md#order-component)

### Decoupled pagination and index preservation: `IndexedOrder`

`order list [PAGE] [--status=STATUS] [--inv=NAME] [--type=TYPE] [--size=SIZE]` is parsed by `OrderCommandParser`
and handled by `OrderHandlers.list`. To decouple the business logic in `OrderHandlers` from console output in
`ResultFormatter` without losing the original position in `OrderHistory`:

1. Filters are applied across the orders in `OrderHistory`.
2. Matching orders are wrapped in an `IndexedOrder(int index, Order order)` record, preserving their 1-based global position.
3. The requested page slice is computed using overflow-safe pagination logic (`(total + size - 1) / size`).
4. `ResultFormatter.orderListIndexed` formats only the requested slice while displaying the global order index,
   ensuring that users can copy displayed numbers directly into lifecycle commands like `deliver`, `reject`, and `cancel`.

[Original contribution in PR #64](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/64/files) ·
[Current Developer Guide](../DeveloperGuide.md#order-component)

### CSV persistence of rejection reasons

`OrderStorage` saves orders as CSV and restores them on startup. When order rejection was introduced:
* The CSV schema was extended to include the `Reason` column.
* Field escaping and parsing were upgraded to support commas and quotation marks within reasons, conforming to standard CSV RFC rules.
* Restoring constructors were implemented to read the rejection reason when the state is `REJECTED`, while gracefully
  handling legacy CSV files without a `Reason` column to ensure backward compatibility.

[Original contribution in PR #46](https://github.com/AY2627S1-CS2113-W14-4/tp/pull/46/files) ·
[Current Developer Guide](../DeveloperGuide.md#order-storage-component)

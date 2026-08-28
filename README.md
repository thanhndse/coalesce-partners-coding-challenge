# Real-Time PnL Engine

An in-memory, incremental futures PnL engine for the Coalesce Partners coding challenge. It loads the supplied opening positions, trades, funding payments, and mark prices; processes events in event-time order; and prints a USDT PnL report grouped by trader and symbol.

## Run

The Gradle wrapper provisions the configured Java 25 toolchain when necessary.

```shell
./gradlew run
```

The optional first argument selects a different directory containing the four input files:

```shell
./gradlew run --args="/path/to/data"
```

Run all tests with:

```shell
./gradlew test
```

The application prints the report to standard output and exits. No database, server, or interactive input is required.

With Docker installed:

```shell
docker build --tag pnl-engine .
docker run --rm pnl-engine
```

## Approach

Startup follows this sequence:

1. Load `opening_positions.csv` as the initial account-level state.
2. Parse eligible rows from `trades.csv`, `funding.csv`, and `prices.csv` into typed events.
3. Sort events by timestamp, then event-type priority, then natural identity.
4. Pass each event to `PnlEngine.process(event)`.
5. Value current positions at the period end and aggregate account states by trader and symbol.
6. Format and print the report.

Position state is never netted across venue accounts during calculation. The engine uses this state key:

```text
(trader, venue, venue_account, symbol)
```

Only reporting uses the coarser key:

```text
(trader, symbol)
```

The central in-memory structures are:

- Account-level position states containing quantity, average entry, cumulative realized PnL, funding, and fees.
- A venue-agnostic ordered price book per symbol.
- Known-event maps for idempotency and correction detection.
- Per-position and per-price-symbol watermarks for late-event detection.
- Pending non-USDT fees whose conversion price is not yet available.

## Cost-basis methodology

The engine uses weighted-average cost because it is compact, deterministic, and well suited to an incremental position state. It avoids retaining individual execution lots while correctly supporting increases, reductions, closure, and crossing through zero.

- Increasing a position recalculates its weighted-average entry.
- Reducing a position realizes PnL against its existing average and leaves that average unchanged.
- Closing a position resets quantity and average entry to zero.
- Crossing through zero realizes the closing portion against the old average and opens the remainder at the new trade price.
- Long realized PnL is `closed quantity * (exit price - average entry)`.
- Short realized PnL is `closed quantity * (average entry - exit price)`.
- Unrealized PnL is `signed quantity * (mark price - average entry)`, which works for both long and short positions.

All parsed quantities and monetary values use `BigDecimal`. Intermediate accounting values are not rounded to a display scale. Weighted-average division uses `MathContext.DECIMAL128` for non-terminating decimal results; only formatted output is rounded to two decimal places using half-even rounding.

## Ordering and idempotency

Initial CSV rows are not assumed to be ordered. Event ordering is:

```text
timestamp -> PRICE before TRADE before FUNDING -> natural identity
```

Price-first ordering at equal timestamps makes a conversion price at exactly the trade timestamp available to that trade. Identity provides deterministic ordering where the dataset supplies no exchange sequence for multiple same-timestamp events.

Natural identities follow the specification:

- Trade: `(venue, trade_id)`
- Funding: `(venue, event_id)`
- Price: `(symbol, timestamp)`

An exact repeat returns `DUPLICATE` without changing state. Reusing an identity with different content throws `ConflictingEventException`; corrections are not silently applied as independent events.

For live processing, a trade or funding event older than the affected account-level position watermark throws `LateEventException`. A price older than the watermark for its symbol does the same. Watermarks are partition-specific, so activity for one trader, account, or symbol does not make an unrelated event late. Exact duplicates are checked before lateness.

This is the required reject policy. The engine does not implement bounded replay.

## Prices, fees, and unavailable values

Price lookup uses the latest price at or before the requested timestamp through `NavigableMap.floorEntry`; a future price is never used.

- A USDT fee is accumulated directly.
- A non-USDT fee is converted with `<fee_asset>USDT` at the trade timestamp.
- If no eligible price exists, the trade and position transition are still applied, but Fees and Total PnL are unavailable.
- If an eligible price subsequently arrives without violating the price watermark, the pending fee is resolved incrementally.
- A non-zero position without an eligible valuation mark has unavailable Unrealized PnL and Total PnL.
- A flat position has zero unrealized PnL and does not require a mark.

Funding is accumulated as the signed amount supplied by the dataset. The dataset guarantees USDT funding; non-USDT funding is rejected rather than converted.

## Calculation boundaries

Trade and funding rows use the half-open interval:

```text
[2026-08-01T00:00:00Z, 2026-08-02T00:00:00Z)
```

Prices follow reference-data rules instead:

- A price before the start remains eligible for a later lookup.
- A price exactly at `2026-08-02T00:00:00Z` is eligible as the closing mark.
- Prices after the valuation timestamp are ignored by the CLI load.

## Validation and edge cases

The implementation and tests cover:

- Long and short opening positions.
- Position increases, reductions, exact closures, and zero crossings.
- Multiple traders, venues, and accounts.
- Account-level valuation before trader/symbol aggregation.
- Unordered input rows and exact duplicate events.
- Conflicting corrections and partition-specific late events.
- USDT and BNB fees.
- Inclusive same-timestamp price lookup and rejection of future-price leakage.
- Missing fee-conversion prices and missing valuation marks.
- Calculation-window boundaries, including the eligible closing price.
- Funding-only or trade-only states without an opening position.

The CSV loader intentionally supports the supplied simple, unquoted schemas rather than being a general RFC 4180 parser. It validates headers, column counts, empty fields, timestamps, numeric fields, sides, instrument type, positive trade quantities/prices, and non-negative fees. Duplicate opening snapshots for the same position key are rejected.

## Tests

The test suite separates position arithmetic, price lookup, engine behavior, CSV boundaries, and full-dataset behavior. The end-to-end test processes the supplied files, verifies all 13 intentional duplicate events are no-ops, confirms all 13 trader/symbol reports are complete, and checks representative rounded totals.

## Trade-offs and improvements

The implementation is deliberately in-memory and single-threaded. That keeps state transitions easy to reason about and is appropriate for the exercise, but the following are outside its current scope:

- Bounded replay for late or corrected events.
- Historical account state for true past as-of reports. `report(timestamp)` values the current processed state using prices available at that timestamp; it does not rewind later trades.
- Durable event storage or checkpoints.
- Concurrent processing and partition ownership.
- Non-USDT funding conversion.
- A general quoted-field CSV parser.

With more time, the first functional improvement would be an ordered event history and checkpoints per account-level position. A late trade could then replay only its affected partition. Price corrections would invalidate only valuations and fee conversions for the affected symbol and time range.

## Production design

At 100 traders, 10 exchanges, 50,000 open positions, and several million daily events, I would retain the explicit event types, account-level partition key, deterministic position transition logic, and separation between state mutation and report formatting.

The first changes would be:

- Partition the event stream by account-level position key, while maintaining a separately partitioned price stream by symbol.
- Persist every validated event to an append-only durable log before acknowledging it.
- Store versioned position snapshots and periodic checkpoints in a transactional state store.
- Make event identity constraints durable so duplicates remain harmless across restarts.
- Rebuild after failure by loading the latest checkpoint and replaying later events from the durable log.
- Track source offsets and checkpoint versions so recovery is deterministic and observable.

For reconciliation, ingest exchange position, balance, funding, fee, and trade snapshots on a schedule. Compare exchange quantities and cost/PnL components with engine state at matching cut-off times, using explicit tolerances. Differences should produce a reconciliation record with trader, account, symbol, source timestamps, and the first divergent event or checkpoint where possible. Operational dashboards and alerts would distinguish timing differences from persistent breaks. Corrections would be represented as auditable events and replayed through affected partitions rather than mutating state manually.

I would initially keep cross-partition reporting simple: consume immutable position snapshots into a reporting store and aggregate there. Distributed computation, complex caching, and additional infrastructure would be introduced only after measured throughput or latency demonstrated the need.

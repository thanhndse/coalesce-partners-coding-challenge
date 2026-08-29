# Real-Time PnL Engine

## How to run the solution

With Docker installed:

```shell
docker build --tag pnl-engine .
docker run --rm pnl-engine
```

With Java installed:

```shell
./gradlew run
```

The Gradle wrapper downloads Gradle and the configured Java 25 toolchain on the
first run if they are not already installed. To load the four CSV files from a
different directory, pass that directory as the optional application argument:

```shell
./gradlew run --args="/path/to/data"
```

Run the test suite with:

```shell
./gradlew test
```

## Approach

Startup follows this sequence:

Report flow:
1. Load `opening_positions.csv` as the initial account-level state.
2. Parse eligible rows from `trades.csv`, `funding.csv`, and `prices.csv` into typed events.
3. Sort events by timestamp, then event-type priority.
4. Pass each event to `PnlEngine.process(event)`.
5. Value current positions at the period end and aggregate account states by trader and symbol.
6. Format and print the report.

Incremental flow:
1. EngineState: Save Position state per key + Price Book (max 50_000 price each symbol) +
Position event history per key (max 1000 events funding + trade) + 1 Position Checkpoint before the events
2. Handle Trade Event ⇒ Dedup + Check Conflicting using an engine-wide `(venue, trade_id)` identity registry
(max 10,000 identities).
Calculate fee + realizedPnl. Replay trade if the timestamp in the past and inside the event ranges, if not reject.
Cost-basis: weighted-average.
3. Handle Funding Event ⇒ Dedup + Check Conflicting using an engine-wide `(venue, event_id)` identity registry
(shared max 10,000 identities).
Calculate funding, late event handle normally by applying.
4. Handle Price ⇒ Add to Price Book (max 50_000 price each symbol), if price is late, then get all the trades affected,
by symbol + time range ⇒ recalculate fee, compare to old fee and apply to current position.

## Cost-basis methodology:

We use weighted average because this is a real-time position and PnL engine. We only need the net position and aggregated PnL; 
we don’t need to know exactly which opening trade each closing trade matches. 
FIFO or LIFO would require us to track individual lots, which adds complexity without changing the total PnL.

## Edge cases handled:

- Duplicate and conflicting trade or funding events.
- Late trades inside the retained history window are replayed in timestamp order.
- Trades older than the replay boundary are rejected and alerted.
- Late prices correct only fees in their affected symbol and time range.
- Missing fee-conversion prices or valuation marks produce unavailable report values.
- Long, short, reduced, closed, and reversed positions.


## Important trade-offs

- We accept bounded replayability: only the latest 1,000 events are retained per position key. 
A late trade within this window can be replayed correctly; 
an older trade cannot be recovered automatically because retaining an unlimited history is not practical. 
We will monitor event volume and increase the limit if necessary.
Extremely late events, such as trades arriving eight hours later, will be placed in a queue for manual processing.

- I chose `BigDecimal` instead of `double` to guarantee predictable decimal precision for financial calculations. 
The trade-off is higher memory usage, slower arithmetic, and more object allocation.
Fixed-point arithmetic using a scaled `long` would be faster and more memory-efficient,
but it introduces additional complexity around scale, rounding, conversions, and overflow. 
Given the current data volume and latency requirements, correctness and maintainability are more important than this optimization.
If measurements later show that numeric processing is a genuine bottleneck, we can migrate performance-critical paths to scaled `long` values.


## Production Design

For production, grows to 100 traders, 10 exchanges, 50,000 open positions, the same processing model should use:

1. At this scale, I would keep the core calculation model simple: each worker maintains the hot state of 
its assigned positions in memory and processes events sequentially per position key. 
Several million events per day is still manageable, so I would not put a database call in the critical processing path.

The first improvements would be high availability. 
I would run a leader with a standby to make sure if 1 fail, another will come up.

If one machine eventually becomes insufficient, I would shard processing by trader.

2. How would you manage and persist state, and rebuild it after a failure:
Every event would be applied as one atomic state transition, 
and a checkpoint would only capture fully completed transitions. 
Every ten minutes, the system would write a consistent checkpoint containing the engine state and the next queue offset 
to storage (AWS EFS and then later archive to S3).
After a failure, the new leader would load the latest completed checkpoint and replay from that offset.

3. For auditing, processed events and resulting position updates would also be written asynchronously to durable storage,
preferably using an outbox or equivalent mechanism to avoid losing updates. Then I will have cron jobs to recalculate 
and compare with the resulting position updates then alert if not match.

## If I have more time and if it is running service in production:
- Implement endpoint to retrieve or update data in memory (JMX) in case incident/monitor state
- Create Monitoring System: count latency, memory, number of events, ⇒ adjust config accordingly 
- Doing the checkpoint + transaction + replay from checkpoint and create test to make sure data after check point load
is correct.
- Benchmark to see how much data we can handle for trade/funding/price.
- Assuming we listen messages of exchange, normalize, then publish again in our queue. Then this is the app listens to
these messages. I will monitor the normalize app as well because if error or latency happened, it may not our app it can
be upstream.

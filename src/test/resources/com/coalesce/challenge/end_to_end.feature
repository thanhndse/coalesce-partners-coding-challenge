Feature: End-to-end PnL processing
  The engine must load complete datasets and remain correct when events arrive late,
  out of order, or more than once.

  Scenario: Produce a complete report from the CSV dataset
    Given the standard CSV dataset
    When the report is generated at the period end
    Then the report contains:
      | trader   | symbol  | finalQuantity | realizedPnl | unrealizedPnl | fundingPnl | fees   | totalPnl |
      | TRADER_A | BTCUSDT | 2.125         | 528.22      | 238.95        | -5.04      | 727.20 | 34.93    |
      | TRADER_A | ETHUSDT | 5.5           | 33.28       | -2.64         | -0.42      | 48.80  | -18.57   |
      | TRADER_A | SOLUSDT | -20           | 2.23        | -10.20        | 1.01       | 65.09  | -72.05   |
      | TRADER_A | XRPUSDT | 4750          | 211.66      | 18.46         | -0.11      | 22.42  | 207.60   |
      | TRADER_B | ETHUSDT | -2            | 118.41      | 6.72          | 9.63       | 209.42 | -74.67   |
      | TRADER_B | SOLUSDT | -15           | -57.31      | -14.90        | -0.12      | 34.77  | -107.09  |
      | TRADER_B | XRPUSDT | -250          | 111.67      | -0.94         | 0.00       | 19.71  | 91.01    |
      | TRADER_C | BTCUSDT | 1.375         | 1224.68     | 599.57        | -12.52     | 350.22 | 1461.52  |
      | TRADER_C | SOLUSDT | 20            | -115.28     | 12.84         | -5.28      | 176.45 | -284.17  |
      | TRADER_C | XRPUSDT | -2500         | -59.53      | -6.44         | -0.32      | 12.72  | -79.01   |
      | TRADER_D | BTCUSDT | -0.375        | 0.00        | -182.56       | 0.87       | 0.00   | -181.69  |
      | TRADER_D | ETHUSDT | 3.75          | 271.27      | -3.68         | 2.94       | 73.38  | 197.16   |
      | TRADER_D | XRPUSDT | 2750          | 23.42       | 14.82         | -0.47      | 23.22  | 14.55    |

  Scenario: Replay a late trade and update the complete report
    Given the engine has this opening position:
      | timestamp            | trader   | venue   | account   | symbol  | quantity | averageEntryPrice |
      | 2026-08-01T00:00:00Z | TRADER_A | BINANCE | ACCOUNT_1 | BTCUSDT | 1        | 100               |
    And these events have been processed in order:
      | type    | timestamp            | trader   | venue   | account   | symbol  | id | side | quantity | price | fee  | asset | amount |
      | PRICE   | 2026-08-01T00:00:00Z |          |         |           | BTCUSDT |    |      |          | 100   |      |       |        |
      | PRICE   | 2026-08-01T00:30:00Z |          |         |           | BNBUSDT |    |      |          | 200   |      |       |        |
      | TRADE   | 2026-08-01T01:00:00Z | TRADER_A | BINANCE | ACCOUNT_1 | BTCUSDT | T1 | BUY  | 1        | 110   | 0.01 | BNB   |        |
      | TRADE   | 2026-08-01T02:00:00Z | TRADER_A | BINANCE | ACCOUNT_1 | BTCUSDT | T2 | SELL | 1        | 130   | 2    | USDT  |        |
      | FUNDING | 2026-08-01T03:00:00Z | TRADER_A | BINANCE | ACCOUNT_1 | BTCUSDT | F1 |      |          |       |      | USDT  | -3     |
      | PRICE   | 2026-08-02T00:00:00Z |          |         |           | BTCUSDT |    |      |          | 120   |      |       |        |
    When these events have been processed in order:
      | type  | timestamp            | trader   | venue   | account   | symbol  | id     | side | quantity | price | fee | asset |
      | TRADE | 2026-08-01T01:30:00Z | TRADER_A | BINANCE | ACCOUNT_1 | BTCUSDT | T_LATE | BUY  | 1        | 120   | 1   | USDT  |
    And the report is generated at the period end
    Then the report contains:
      | trader   | symbol  | finalQuantity | realizedPnl | unrealizedPnl | fundingPnl | fees | totalPnl |
      | TRADER_A | BTCUSDT | 2             | 20.00       | 20.00         | -3.00      | 5.00 | 32.00    |

  Scenario: Replay every event when another late trade shares the oldest timestamp
    Given the engine has this opening position:
      | timestamp            | trader   | venue   | account   | symbol  | quantity | averageEntryPrice |
      | 2026-08-01T00:00:00Z | TRADER_A | BINANCE | ACCOUNT_1 | BTCUSDT | 1        | 100               |
    And these events have been processed in order:
      | type    | timestamp            | trader   | venue   | account   | symbol  | id | side | quantity | price | fee  | asset | amount |
      | PRICE   | 2026-08-01T00:00:00Z |          |         |           | BTCUSDT |    |      |          | 100   |      |       |        |
      | PRICE   | 2026-08-01T00:30:00Z |          |         |           | BNBUSDT |    |      |          | 200   |      |       |        |
      | TRADE   | 2026-08-01T01:00:00Z | TRADER_A | BINANCE | ACCOUNT_1 | BTCUSDT | T1 | BUY  | 1        | 110   | 0.01 | BNB   |        |
      | TRADE   | 2026-08-01T02:00:00Z | TRADER_A | BINANCE | ACCOUNT_1 | BTCUSDT | T2 | SELL | 1        | 130   | 2    | USDT  |        |
      | FUNDING | 2026-08-01T03:00:00Z | TRADER_A | BINANCE | ACCOUNT_1 | BTCUSDT | F1 |      |          |       |      | USDT  | -3     |
      | PRICE   | 2026-08-02T00:00:00Z |          |         |           | BTCUSDT |    |      |          | 120   |      |       |        |
    When these events have been processed in order:
      | type    | timestamp            | trader   | venue   | account   | symbol  | id               | side | quantity | price | fee  | asset | amount |
      | TRADE   | 2026-08-01T01:30:00Z | TRADER_A | BINANCE | ACCOUNT_1 | BTCUSDT | T_LATE           | BUY  | 1        | 120   | 1    | USDT  |        |
      | FUNDING | 2026-08-01T01:15:00Z | TRADER_A | BINANCE | ACCOUNT_1 | BTCUSDT | F_LATE           |      |          |       |      | USDT  | -4     |
      | TRADE   | 2026-08-01T01:00:00Z | TRADER_A | BINANCE | ACCOUNT_1 | BTCUSDT | T_SAME_TIMESTAMP | BUY  | 1        | 90    | 0.02 | BNB   |        |
    And the report is generated at the period end
    Then the report contains:
      | trader   | symbol  | finalQuantity | realizedPnl | unrealizedPnl | fundingPnl | fees | totalPnl |
      | TRADER_A | BTCUSDT | 3             | 25.00       | 45.00         | -7.00      | 9.00 | 54.00    |

  Scenario: Mark fees and total PnL unavailable when a fee cannot be converted
    Given the engine has this opening position:
      | timestamp            | trader   | venue   | account   | symbol  | quantity | averageEntryPrice |
      | 2026-08-01T00:00:00Z | TRADER_A | BINANCE | ACCOUNT_1 | BTCUSDT | 1        | 100               |
    When these events have been processed in order:
      | type  | timestamp            | trader   | venue   | account   | symbol  | id | side | quantity | price | fee  | asset | amount |
      | TRADE | 2026-08-01T00:00:01Z | TRADER_A | BINANCE | ACCOUNT_1 | BTCUSDT | T1 | BUY  | 1        | 110   | 0.01 | BNB   |        |
      | PRICE | 2026-08-01T00:00:07Z |          |         |           | BTCUSDT |    |      |          | 140   |      |       |        |
    And the report is generated at 2026-08-01T00:00:07Z
    Then the report contains:
      | trader   | symbol  | finalQuantity | realizedPnl | unrealizedPnl | fundingPnl | fees        | totalPnl   |
      | TRADER_A | BTCUSDT | 2             | 0.00        | 70.00         | 0.00       | UNAVAILABLE | UNAVAILABLE |

  Scenario: Resolve an unavailable fee when its price arrives
    Given the engine has this opening position:
      | timestamp            | trader   | venue   | account   | symbol  | quantity | averageEntryPrice |
      | 2026-08-01T00:00:00Z | TRADER_A | BINANCE | ACCOUNT_1 | BTCUSDT | 1        | 100               |
    When these events have been processed in order:
      | type  | timestamp            | trader   | venue   | account   | symbol  | id | side | quantity | price | fee  | asset |
      | TRADE | 2026-08-01T00:00:02Z | TRADER_A | BINANCE | ACCOUNT_1 | BTCUSDT | T1 | BUY  | 1        | 110   | 0.01 | BNB   |
      | PRICE | 2026-08-01T00:00:07Z |          |         |           | BTCUSDT |    |      |          | 120   |      |       |
      | PRICE | 2026-08-01T00:00:02Z |          |         |           | BNBUSDT |    |      |          | 15    |      |       |
    And the report is generated at 2026-08-01T00:00:07Z
    Then the report contains:
      | trader   | symbol  | finalQuantity | realizedPnl | unrealizedPnl | fundingPnl | fees | totalPnl |
      | TRADER_A | BTCUSDT | 2             | 0.00        | 30.00         | 0.00       | 0.15 | 29.85    |

  Scenario: Apply a late price only until the next retained price
    Given the engine has this opening position:
      | timestamp            | trader   | venue   | account   | symbol  | quantity | averageEntryPrice |
      | 2026-08-01T00:00:00Z | TRADER_A | BINANCE | ACCOUNT_1 | BTCUSDT | 1        | 100               |
    When these events have been processed in order:
      | type  | timestamp            | trader   | venue   | account   | symbol  | id | side | quantity | price | fee  | asset |
      | PRICE | 2026-08-01T00:00:01Z |          |         |           | BNBUSDT |    |      |          | 10    |      |       |
      | PRICE | 2026-08-01T00:00:03Z |          |         |           | BNBUSDT |    |      |          | 20    |      |       |
      | TRADE | 2026-08-01T00:00:02Z | TRADER_A | BINANCE | ACCOUNT_1 | BTCUSDT | T1 | BUY  | 1        | 110   | 0.01 | BNB   |
      | TRADE | 2026-08-01T00:00:04Z | TRADER_A | BINANCE | ACCOUNT_1 | BTCUSDT | T2 | BUY  | 1        | 110   | 0.01 | BNB   |
      | PRICE | 2026-08-01T00:00:07Z |          |         |           | BTCUSDT |    |      |          | 120   |      |       |
      | PRICE | 2026-08-01T00:00:02Z |          |         |           | BNBUSDT |    |      |          | 15    |      |       |
    And the report is generated at 2026-08-01T00:00:07Z
    Then the report contains:
      | trader   | symbol  | finalQuantity | realizedPnl | unrealizedPnl | fundingPnl | fees | totalPnl |
      | TRADER_A | BTCUSDT | 3             | 0.00        | 40.00         | 0.00       | 0.35 | 39.65    |

  Scenario: Mark unrealized and total PnL unavailable when the valuation mark is missing
    Given the engine has this opening position:
      | timestamp            | trader   | venue   | account   | symbol  | quantity | averageEntryPrice |
      | 2026-08-01T00:00:00Z | TRADER_A | BINANCE | ACCOUNT_1 | BTCUSDT | 1        | 100               |
    When the report is generated at 2026-08-01T00:00:07Z
    Then the report contains:
      | trader   | symbol  | finalQuantity | realizedPnl | unrealizedPnl | fundingPnl | fees | totalPnl   |
      | TRADER_A | BTCUSDT | 1             | 0.00        | UNAVAILABLE   | 0.00       | 0.00 | UNAVAILABLE |

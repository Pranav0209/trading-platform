# first run contracts/analytics-schema.sql

# load from analytics.duckdb

import duckdb

connection = duckdb.connect('analytics.duckdb')
trades = connection.sql("SELECT * FROM fact_candles").df()

print(trades)



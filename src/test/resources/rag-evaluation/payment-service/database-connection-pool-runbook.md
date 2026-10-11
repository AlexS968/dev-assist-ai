# Payment Service database connection pool runbook

When Payment API returns HTTP 503 with HikariCP connection acquisition timeout, inspect active, idle, and pending connections. Compare HikariCP metrics with PostgreSQL max_connections and available database capacity. Identify slow queries, long transactions, and idle-in-transaction sessions before changing settings. Do not increase pool size without checking PostgreSQL capacity and the combined pool sizes of dependent services. Mitigate the blocking query or transaction first, then verify pending connections and error rate recover.

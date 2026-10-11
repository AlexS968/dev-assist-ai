# Order Service pool capacity note

ORDER_SERVICE_ONLY. PostgreSQL max_connections constrains the aggregate HikariCP pool capacity of Order Service replicas. Inspect idle-in-transaction sessions before changing pool size. This document is unrelated to Payment Service operations.

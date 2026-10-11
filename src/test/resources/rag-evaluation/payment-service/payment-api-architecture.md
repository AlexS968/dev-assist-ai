# Payment API architecture

Payment API accepts payment requests and stores transaction state in PostgreSQL. It uses a HikariCP connection pool and calls a fictional settlement adapter. The frontend authenticates separately. This overview describes dependencies and data flow; operational troubleshooting belongs in service runbooks.

# JPA and Hibernate example

Runnable code behind [JPA_and_Hibernate_Deep_Dive_Notes.md](../../JPA_and_Hibernate_Deep_Dive_Notes.md).
Spring Boot 4.1.1, Hibernate 7.4.5, in-memory H2. **31 tests.**

```
mvn test
mvn -Dtest=SqlShowcaseTest test      # prints the real SQL behind N+1, join fetch, projections, locking, bulk update
```

| Test class | Shows |
|---|---|
| `PersistenceTest` (22) | first-level cache, dirty checking, entity states, owning side, cascades, orphan removal, N+1 and its fixes, paging, projections, bulk update, ids and batching |
| `BatchFetchTest` (1) | `default_batch_fetch_size`: 5 authors' books in 2 queries |
| `TransactionTest` (7) | rollback rules, `REQUIRES_NEW`, self-invocation, lazy loading after the transaction, optimistic locking |
| `SqlShowcaseTest` | prints SQL (no assertions of note) |

`SqlRecorder` is a Hibernate `StatementInspector` that records every SQL statement so tests can count them.
Requires JDK 17+ and Maven.

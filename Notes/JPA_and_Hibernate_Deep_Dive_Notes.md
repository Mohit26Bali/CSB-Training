# JPA and Hibernate Deep Dive: Detailed Notes

The follow-up to the *JDBC, JPA and Spring Data* notes: what really happens **inside** JPA. The persistence context (why changes are saved without calling `save`), relationships and who owns them, lazy loading and the exception it throws, the **N+1 problem** and four ways to fix it, projections, paging, bulk updates, locking, transactions that commit when you expected a rollback, batching, and the mistakes that make JPA applications slow.

> **Everything here was run, and the SQL is the real SQL.** The examples are in `examples/jpa-hibernate`: **31 tests, all passing**, on **Spring Boot 4.1.1, Hibernate 7.4.5, Spring Data JPA 4.1.1, Jakarta Persistence 3.2** and an in-memory H2 database. The tests count and inspect the SQL Hibernate sends (with a `StatementInspector`), so claims like "this needs one query, not four" were measured. Where Hibernate 6 / Spring Boot 3 behaves differently, a note says so.

---

## Table of Contents

1. [The Layers: JDBC, JPA, Hibernate, Spring Data JPA](#1-the-layers-jdbc-jpa-hibernate-spring-data-jpa)
2. [Setup and the Example Model](#2-setup-and-the-example-model)
3. [Mapping Essentials](#3-mapping-essentials)
4. [The Persistence Context](#4-the-persistence-context)
5. [Relationships](#5-relationships)
6. [Fetching, Lazy Loading and `LazyInitializationException`](#6-fetching-lazy-loading-and-lazyinitializationexception)
7. [The N+1 Problem and Its Fixes](#7-the-n1-problem-and-its-fixes)
8. [Queries, Projections and Paging](#8-queries-projections-and-paging)
9. [Bulk Updates and the Stale Persistence Context](#9-bulk-updates-and-the-stale-persistence-context)
10. [Transactions: Commit, Rollback and Propagation](#10-transactions-commit-rollback-and-propagation)
11. [Locking: Optimistic and Pessimistic](#11-locking-optimistic-and-pessimistic)
12. [Identifiers, Batching and Performance](#12-identifiers-batching-and-performance)
13. [`equals`, `hashCode`, `toString` and DTOs](#13-equals-hashcode-tostring-and-dtos)
14. [Schema Management](#14-schema-management)
15. [Testing JPA Code](#15-testing-jpa-code)
16. [Common Mistakes](#16-common-mistakes)
17. [Output Prediction Puzzles](#17-output-prediction-puzzles)
18. [Interview Questions](#18-interview-questions)
19. [Cheat Sheet](#19-cheat-sheet)
20. [Practice Tasks](#20-practice-tasks)

---

## 1. The Layers: JDBC, JPA, Hibernate, Spring Data JPA

```
Your code      ->  Spring Data JPA   (repositories: findByName, save, Pageable)
                       |
                   JPA  (the standard API: @Entity, EntityManager, JPQL)         <- jakarta.persistence.*
                       |
                   Hibernate  (the implementation: SQL generation, caching, dirty checking)
                       |
                   JDBC  (Connection, PreparedStatement)  ->  database driver  ->  database
```

| Layer | What it is | You use it for |
|---|---|---|
| **JDBC** | the low-level Java database API | (rarely directly) |
| **JPA** | a **specification**: annotations and the `EntityManager` interface | mapping classes to tables, writing JPQL |
| **Hibernate** | the most common JPA **implementation** (also has extras: `@BatchSize`, `Statistics`) | the engine; you tune it |
| **Spring Data JPA** | generates repository implementations on top of JPA | 90% of day-to-day data access |

**ORM** (object-relational mapping) exists because objects (references, inheritance, collections) and tables (rows, foreign keys, joins) do not match. JPA bridges the gap, and gives you a **unit of work** (changes collected and written together), but it also **hides SQL**: if you do not know what it sends, you will ship the slow queries in this note.

> Rule: with JPA, **always look at the SQL** (and count it) for every feature. The example project shows how.

---

## 2. Setup and the Example Model

### Dependencies (Spring Boot 4)

```xml
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
<dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><scope>runtime</scope></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa-test</artifactId><scope>test</scope></dependency>
```

For production replace H2 with the PostgreSQL or MySQL driver. (Boot 3: `spring-boot-starter-test` provides `@DataJpaTest`.)

### Settings used in the examples

```properties
spring.datasource.url=jdbc:h2:mem:library;DB_CLOSE_DELAY=-1
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.properties.hibernate.generate_statistics=true       # counters: statements, loads, updates
spring.jpa.properties.hibernate.jdbc.batch_size=20             # group inserts/updates into JDBC batches
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.open-in-view=false                                  # see section 6
```

To print SQL while developing: `spring.jpa.show-sql=true` and `logging.level.org.hibernate.orm.jdbc.bind=TRACE` (parameter values). To **count and assert** SQL in tests, register a `StatementInspector`:

```java
public class SqlRecorder implements StatementInspector {
    private static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();
    @Override public String inspect(String sql) { STATEMENTS.add(sql); return sql; }
    public static void clear() { STATEMENTS.clear(); }
    public static long count(String startsWith) { ... }
}
// @DataJpaTest(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector=lib.SqlRecorder")
```

### The model: authors, books, tags

```
Author 1 ----- * Book * ----- * Tag
```

```java
@Entity
public class Author {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 80) private String name;
    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Book> books = new ArrayList<>();
    protected Author() { }                                     // JPA needs a no-arg constructor
    public Author(String name) { this.name = name; }
    public void addBook(Book book)    { books.add(book);    book.setAuthor(this); }   // keep BOTH sides in sync
    public void removeBook(Book book) { books.remove(book); book.setAuthor(null); }
}

@Entity
public class Book {
    public enum Status { DRAFT, PUBLISHED, OUT_OF_PRINT }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String title;
    private int pages;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status = Status.DRAFT;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "author_id") private Author author;
    @ManyToMany @JoinTable(name = "book_tag", joinColumns = @JoinColumn(name = "book_id"),
                           inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new HashSet<>();
    @Version private long version;                             // optimistic locking (section 11)
}
```

### The schema Hibernate 7 generated for it (H2)

```sql
create sequence audit_seq start with 1 increment by 50;
create table audit_log (id bigint not null, message varchar(255), primary key (id));
create table author (id bigint generated by default as identity, name varchar(80) not null, primary key (id));
create table book (pages integer not null, author_id bigint not null, id bigint generated by default as identity,
                   version bigint not null, title varchar(255) not null,
                   status enum ('DRAFT','OUT_OF_PRINT','PUBLISHED') not null, primary key (id));
create table book_tag (book_id bigint not null, tag_id bigint not null, primary key (book_id, tag_id));
create table tag (id bigint generated by default as identity, name varchar(40) not null unique, primary key (id));
alter table book add constraint ... foreign key (author_id) references author;
alter table book_tag add constraint ... foreign key (tag_id) references tag;
alter table book_tag add constraint ... foreign key (book_id) references book;
```

Notice: the foreign key `author_id` is in `book` (the "many" side), the many-to-many needs a **join table**, `@Column(nullable = false)` became `not null`, and `@Enumerated(STRING)` became an enum column (a `varchar` on other databases).

---

## 3. Mapping Essentials

| Annotation | Meaning |
|---|---|
| `@Entity` | this class is persistent (needs a no-arg constructor, non-final class, an `@Id`) |
| `@Table(name = "...", indexes = ..., uniqueConstraints = ...)` | table name and constraints |
| `@Id` | primary key |
| `@GeneratedValue(strategy = ...)` | how the key is created: `IDENTITY` (auto-increment column), `SEQUENCE` (a database sequence), `UUID`, `AUTO` |
| `@Column(name, nullable, length, unique, precision, scale)` | column details |
| `@Enumerated(EnumType.STRING)` | store an enum by **name** (never `ORDINAL`: reordering constants corrupts data) |
| `@Version` | optimistic-locking counter (section 11) |
| `@Transient` | not persisted |
| `@Lob` | large text or binary |
| `@Embedded` / `@Embeddable` | a value object stored in the owner's table (an `Address` with street and city columns) |
| `@Temporal` | not needed for `java.time` types: `LocalDate`, `Instant`... map directly |

Rules of thumb:

- Use **`Long`/`UUID` surrogate keys** (a meaningless generated id), and put a unique constraint on the natural key (an email).
- Use `java.time` types, `BigDecimal` for money (precision and scale!), enums as `STRING`.
- **Constraints belong in the database too:** `nullable = false`, `unique = true`, foreign keys. Bean-validation annotations (`@NotBlank`) protect the application layer; the database is the last line of defence against concurrent bugs.
- Put business rules in the entity (methods like `addBook`, `publish()`), not only getters and setters.
- Keep entities out of your REST API (section 13).

---

## 4. The Persistence Context

The **persistence context** (also: the first-level cache, the `Session`) is a set of entities the current transaction is **managing**. It gives JPA three behaviours that surprise newcomers.

### 4.1 One object per row, and no repeated queries (first-level cache)

```java
Book first  = books.findById(id).orElseThrow();
Book second = books.findById(id).orElseThrow();
assertThat(second).isSameAs(first);                 // the very same object
assertThat(SqlRecorder.selects()).isEqualTo(1);     // one SELECT, not two
```

Within a transaction, loading the same id twice gives the **same instance** and no second query. (This cache lives only as long as the transaction; it is not a performance cache across requests.)

### 4.2 Dirty checking: changes are saved without calling `save`

```java
Book book = books.findById(id).orElseThrow();
book.setTitle("A new title");                       // no save(), no persist()
em.flush();                                         // happens automatically when the transaction commits
// -> exactly one: update book set ..., version=? where id=? and version=?
```

Hibernate keeps a **snapshot** of each loaded entity and compares it with the current state at flush time; any difference becomes an `UPDATE`. So inside a `@Transactional` method you usually do **not** need `repository.save(entity)` for something you just loaded. (Verified: one `update book`, and `version` went from 0 to 1.)

### 4.3 Flushing

The context is written to the database at **flush**: before a query that might be affected (flush mode `AUTO`), at commit, or when you call `flush()`. `saveAndFlush`/`flush()` are useful when you need the database constraints checked now (a unique violation surfaces at flush, not at `save`).

### 4.4 Entity states

```
new/transient --persist--> managed --detach/close/clear--> detached
                              |  ^                              |
                          remove  \---------- merge ------------/
                              v
                           removed
```

```java
Author fresh = new Author("Dev");
em.contains(fresh);                  // false: transient
em.persist(fresh);
em.contains(fresh);                  // true: managed (tracked)
em.flush(); em.detach(fresh);
em.contains(fresh);                  // false: detached
fresh.setName("Dev Renamed");        // changes to a detached object are NOT written (verified: no UPDATE)
Author managed = em.merge(fresh);    // copies the state onto a NEW managed instance
managed != fresh                     // true: keep using the returned object
```

| Operation | Does |
|---|---|
| `persist(entity)` | makes a **new** entity managed (INSERT at flush); the same instance becomes managed |
| `merge(entity)` | copies the state of a **detached or new** object onto a managed instance, returned; used for updates from outside the persistence context |
| `remove(entity)` | schedules a DELETE |
| `detach` / `clear` | stop tracking one / all entities |
| `refresh(entity)` | reload from the database, discarding changes |
| `find(Class, id)` | loads by id (SELECT now) |
| `getReference(Class, id)` | returns a **lazy proxy** with no SELECT until you use a field (verified); handy for setting a foreign key without loading the parent |

### 4.5 What `repository.save` really does

`SimpleJpaRepository.save` calls **`persist`** if the entity is new (`id == null`, or the `@Version` is unset) and **`merge`** otherwise. `merge` first does a `SELECT` to load the current row. So saving a detached object with an assigned id costs an extra query; and the returned instance is the managed one: `author = repo.save(author)`.

---

## 5. Relationships

### The owning side decides what is written

In a bidirectional relationship **one side owns the foreign key**: the side **without** `mappedBy`. For `Author` and `Book`, the foreign key `author_id` lives in `book`, so **`Book.author` is the owner** and `Author.books` is the inverse (`mappedBy = "author"`). Hibernate writes only the owning side.

```java
Book stray = new Book("Not linked", 50);
asha.getBooks().add(stray);          // only the inverse side was set; stray.author is still null
em.persist(stray); em.flush();       // FAILS: author_id is NOT NULL and the owner is null (verified)
```

That is why the entity has the **helper methods** `addBook` / `removeBook` that set both sides. Always use them.

### Cascades and orphan removal

```java
@OneToMany(mappedBy = "author", cascade = CascadeType.ALL, orphanRemoval = true)
```

| Option | Effect |
|---|---|
| `cascade = PERSIST` | saving the author also saves new books |
| `cascade = REMOVE` | deleting the author deletes the books (verified: deleting Asha removed her 3 books) |
| `cascade = ALL` | persist, merge, remove, refresh, detach |
| `orphanRemoval = true` | a child **removed from the parent's collection** is deleted (verified: after `asha.removeBook(first)` the row is gone) |

Use cascades **only from a parent to children it owns** (an order and its lines). Never cascade `REMOVE` across a many-to-many (deleting a book must not delete shared tags), nor from a child up to a parent.

### One-to-many, many-to-one, many-to-many

| Relationship | Notes |
|---|---|
| `@ManyToOne` | the most common and cheapest; **set `fetch = LAZY`** explicitly (the default is `EAGER`) |
| `@OneToMany(mappedBy=...)` | the inverse side; default `LAZY`; prefer a **bidirectional** mapping with `mappedBy` over a unidirectional `@OneToMany` (which creates an extra join table or extra UPDATEs) |
| `@OneToOne` | share a primary key (`@MapsId`) or a unique foreign key; `LAZY` on the non-owning side cannot always be proxied: test it |
| `@ManyToMany` | a join table (`book_tag`, 9 rows in the tests); use a **`Set`**, not a `List` (removing from a `List` deletes and re-inserts all rows); if the link needs extra columns (date added), make a **real entity** for the link table |

Fetch defaults you must remember:

| Association | Default |
|---|---|
| `@ManyToOne`, `@OneToOne` | **EAGER** (change to LAZY) |
| `@OneToMany`, `@ManyToMany` | **LAZY** |

---

## 6. Fetching, Lazy Loading and `LazyInitializationException`

A **lazy** association is a placeholder (a proxy or a special collection) that loads when first used. This saves queries you never need, but the load needs an open `Session`:

```java
Author loaded = authors.findByName("Lazy").orElseThrow();   // the repository call ends its transaction here
loaded.getBooks().size();
// -> org.hibernate.LazyInitializationException: could not initialize proxy - no Session     (verified)

// inside a transaction it works
int size = tx.execute(status -> authors.findByName("Lazy").orElseThrow().getBooks().size());   // 1
```

### Fixing it, in order of preference

1. **Load what you need in the query**: `join fetch`, an `@EntityGraph`, or a projection (section 7 and 8). Best: the data you want is loaded in advance.
2. Do the work **inside a `@Transactional` service method** and return a **DTO** (do not return a lazy entity to the web layer).
3. `Hibernate.initialize(author.getBooks())` inside the transaction, for a specific case.

Not fixes: making everything `EAGER` (loads giant graphs and causes N+1 anyway), or **Open Session In View**.

### Open Session In View (OSIV)

Spring Boot **enables it by default** (`spring.jpa.open-in-view=true`) and warns about it at start-up. The session stays open until the HTTP response is written, so lazy loading works in controllers and in JSON serialization. That hides the problem and issues queries **during view rendering** (one more N+1 source), holds a database connection for the whole request, and moves transaction thinking out of the service layer. Set it to **false** and fetch deliberately.

---

## 7. The N+1 Problem and Its Fixes

**The problem:** one query loads N parents, then **one more query per parent** loads each child collection: `1 + N` queries. It is invisible in the code (`a.getBooks().size()` looks harmless) and is the most common JPA performance bug.

```java
List<Author> all = authors.findAll();                        // 1 query
for (Author a : all) total += a.getBooks().size();           // + 1 query per author
// 3 authors -> 4 queries (verified)
```

The SQL (2 authors):

```sql
select a1_0.id,a1_0.name from author a1_0
select b1_0.author_id,b1_0.id,b1_0.pages,b1_0.status,b1_0.title,b1_0.version from book b1_0 where b1_0.author_id=?
select b1_0.author_id,b1_0.id,b1_0.pages,b1_0.status,b1_0.title,b1_0.version from book b1_0 where b1_0.author_id=?
```

With 1,000 authors that is 1,001 round trips to the database.

### Fix 1: `JOIN FETCH`

```java
@Query("select distinct a from Author a join fetch a.books order by a.name")
List<Author> findAllWithBooks();
```

```sql
select distinct a1_0.id,b1_0.author_id,b1_0.id,b1_0.pages,b1_0.status,b1_0.title,b1_0.version,a1_0.name
from author a1_0 join book b1_0 on a1_0.id=b1_0.author_id order by a1_0.name
```

**One query** (verified: 1 SELECT for 3 authors and 9 books). `join` (inner) drops authors with no books: use `left join fetch` to keep them.

### Fix 2: `@EntityGraph`

```java
@EntityGraph(attributePaths = "books")
@Query("select a from Author a order by a.name")
List<Author> findAllWithBooksGraph();
```

The same single query without writing the join (it is a left join), and it can be added to derived queries (`@EntityGraph(attributePaths = "books") List<Author> findByNameStartingWith(...)`).

### Fix 3: batch fetching

When fetching joins are awkward, tell Hibernate to load collections for several parents at once:

```properties
spring.jpa.properties.hibernate.default_batch_fetch_size=10      # or @BatchSize(size = 10) on the association
```

With 5 authors the books were loaded with **2 queries** instead of 6: the authors, then one `where author_id in (?, ?, ?, ...)` (verified in `BatchFetchTest`). A cheap global safety net: you still pay a few queries, but not N.

### Fix 4: fetch only what the screen needs (projections)

If you only need the author's name and a count, do not load entities at all (section 8): one query, no entities to track.

### Rules for fetch joins

| Rule | Why |
|---|---|
| Fetch **one collection** per query | two `join fetch` collections multiply rows (a Cartesian product) and Hibernate rejects "multiple bag fetch" for `List`s |
| `ManyToOne` fetch joins are cheap and fine | no row multiplication |
| Fetch to a **depth you need** | a deep graph in one query can be huge |
| Measure | count statements in a test; look at `hibernate.generate_statistics` |

### Paging with a collection fetch: Hibernate 6 vs 7

`select distinct a from Author a join fetch a.books` with `setMaxResults(2)`:

- **Hibernate 6 and earlier** loaded **every row** and cut the list **in memory**, logging warning `HHH90003004` ("firstResult/maxResults specified with collection fetch; applying in memory"). With 100,000 rows that is an out-of-memory risk. The usual workaround was two queries: page the ids first, then fetch the entities `where id in (:ids)`.
- **Hibernate 7 (Spring Boot 4)** pages the **root rows in a subquery** and joins the collection to them, so the limit is applied in the database (verified):

```sql
select a1_0.id,b1_0.author_id, ... from
  (select distinct a1_0.id,a1_0.name from author a1_0
   where exists(select 1 from book b1_0 where a1_0.id=b1_0.author_id)
   order by a1_0.name offset ? rows fetch first ? rows only) a1_0(id,name)
join book b1_0 on a1_0.id=b1_0.author_id order by a1_0.name
```

Result: the first two authors, each with their **complete** book list (6 books). If you are on Boot 3 / Hibernate 6, check the log for `HHH90003004` and use the two-query pattern.

---

## 8. Queries, Projections and Paging

### Derived queries (Spring Data builds the JPQL from the method name)

```java
Optional<Author> findByName(String name);
boolean existsByName(String name);
List<Author> findByNameStartingWithIgnoreCaseOrderByNameAsc(String prefix);
List<Book> findByPagesGreaterThanOrderByPagesDesc(int pages);
List<Book> findByStatus(Book.Status status);
```

Keywords: `And`, `Or`, `Between`, `LessThan`, `Like`, `Containing`, `StartingWith`, `IgnoreCase`, `In`, `IsNull`, `OrderBy`, `Top3`, `Distinct`, `CountBy`, `ExistsBy`, `DeleteBy`. A name that grows past three conditions is a sign to use `@Query`.

### `@Query`: JPQL and native

```java
@Query("select a from Author a where lower(a.name) like lower(concat('%', :text, '%')) order by a.name")
List<Author> search(@Param("text") String text);

@Query(value = "select * from author where name = ?1", nativeQuery = true)      // raw SQL: use sparingly, loses portability
Optional<Author> findByNameNative(String name);
```

**JPQL** queries **entities and their fields** (`from Author a`, `a.books`), not tables and columns. Always use **parameters** (`:text`), never string concatenation: parameters are sent separately, which prevents SQL injection and lets the database reuse plans.

### Projections: read only what you need

```java
// 1. a record (a "DTO projection"), built by the query itself
record AuthorSummary(String name, long bookCount) { }

@Query("select new lib.repo.AuthorRepository$AuthorSummary(a.name, count(b)) from Author a left join a.books b group by a.id, a.name order by a.name")
List<AuthorSummary> summaries();
```

```sql
select a1_0.name,count(b1_0.id) from author a1_0 left join book b1_0 on a1_0.id=b1_0.author_id group by a1_0.id,a1_0.name order by a1_0.name
```

One query, **no entities** (no persistence context overhead, no lazy loading, no accidental `pages` column).

```java
// 2. an interface projection: Spring creates a proxy over just these columns
interface TitleAndPages { String getTitle(); int getPages(); }
List<TitleAndPages> findByAuthorName(String authorName);
```

```sql
select b1_0.title,b1_0.pages from book b1_0 join author a1_0 on a1_0.id=b1_0.author_id where a1_0.name=?
```

Only `title` and `pages` were selected (verified: `status` is not in the SQL). Entities are for **changing** data; projections are for **reading** it (lists, reports, search results).

### Paging and sorting

```java
@Query("select a from Author a")
Page<Author> page(Pageable pageable);

Page<Author> page = authors.page(PageRequest.of(0, 2, Sort.by("name")));
page.getContent();         // 2 authors
page.getTotalElements();   // 3 (this needs an EXTRA count query: verified)
page.getTotalPages();      // 2
```

| Type | Cost |
|---|---|
| `Page<T>` | the page **plus** a `select count(...)` for the total |
| `Slice<T>` | the page only, fetching one extra row to know whether a "next" page exists |
| `List<T>` + `Pageable` | the page only |

Deep `OFFSET` pages (page 50,000) are slow because the database still walks past all earlier rows: use **keyset paging** (`where id > :lastId order by id limit 20`) for infinite scroll and exports. Always `Sort` by a unique column too (add `id`) so pages do not overlap.

### Specifications and Criteria

For searches with many optional filters (a filter form), `JpaSpecificationExecutor` with `Specification<T>` composes conditions in Java instead of a dozen `@Query` variants; Querydsl is the popular alternative. The principles above (parameters, fetch only what is needed) still apply.

---

## 9. Bulk Updates and the Stale Persistence Context

```java
@Modifying
@Query("update Book b set b.status = :status where b.pages < :pages")
int markShortBooks(@Param("status") Book.Status status, @Param("pages") int pages);
```

```sql
update book b1_0 set status=? where b1_0.pages<?        -- one statement, no entities loaded
```

A bulk update or delete is **one SQL statement** (fast for many rows), but it **bypasses the persistence context** and `@Version`:

```java
Book book = ...;                                              // already loaded, status DRAFT
books.markShortBooks(Book.Status.OUT_OF_PRINT, 400);
book.getStatus();                                             // still DRAFT: the object in memory is STALE (verified)
em.clear();
books.findById(id).get().getStatus();                         // OUT_OF_PRINT: the database has it
```

Fix: `@Modifying(clearAutomatically = true, flushAutomatically = true)` flushes pending changes first and clears the context afterwards, so later reads are fresh (verified). Bulk operations do not trigger cascades, lifecycle callbacks (`@PreUpdate`) or optimistic-lock version increments.

---

## 10. Transactions: Commit, Rollback and Propagation

`@Transactional` (Spring) wraps a method in a database transaction through a **proxy**. Everything in the earlier notes about proxies applies (the method must be public, called from another bean).

### What rolls back? (all verified in `TransactionTest`)

| Method throws | Result |
|---|---|
| **unchecked** exception (`RuntimeException`, `Error`) | **rolled back** |
| **checked** exception (`IOException`) | **COMMITTED anyway** (by default!) |
| checked exception with `@Transactional(rollbackFor = IOException.class)` | rolled back |

```java
@Transactional
public void addAuthorThenFailWithCheckedException(String name) throws IOException {
    authors.save(new Author(name));
    throw new IOException("disk full");                  // the author IS saved: a classic interview question
}
```

Spring follows the EJB convention: a checked exception is treated as a business outcome the caller may handle, an unchecked one as a failure. When in doubt, **`rollbackFor = Exception.class`**. Also, **catching an exception inside the `@Transactional` method** means the proxy never sees it, so the transaction commits (or is marked rollback-only and fails at commit).

### Propagation

| `Propagation` | Behaviour |
|---|---|
| `REQUIRED` (default) | join the caller's transaction, or start one |
| `REQUIRES_NEW` | **suspend** the caller's transaction and run in a **new, separate** one that commits or rolls back on its own |
| `SUPPORTS`, `NOT_SUPPORTED`, `NEVER` | run with or without a transaction as stated |
| `MANDATORY` | must already be inside one, else an error |
| `NESTED` | a savepoint inside the current transaction (JDBC savepoints; not supported by all setups) |

`REQUIRES_NEW` for an audit log: the main work fails and rolls back, but the audit row **survives** (verified):

```java
@Transactional
public void addAuthorWithAuditThenFail(String name) {
    authors.save(new Author(name));
    auditWriter.write("tried to add " + name);          // a different bean: @Transactional(REQUIRES_NEW)
    throw new IllegalStateException("boom");
}
// result: author NOT saved, audit row saved
```

The audit writer must be **another bean**. When the same method called `this.writeAudit(...)` (self-invocation) the proxy was bypassed, no new transaction began, and the audit row was **lost** with the rollback (verified).

### More transaction rules

- `@Transactional(readOnly = true)` on query-only methods: a hint that lets Hibernate skip dirty checking and some databases route to a replica. It does not forbid writes in every setup.
- Keep transactions **short**: no HTTP calls, file uploads or `Thread.sleep` inside one (they hold a connection and locks).
- Put `@Transactional` on the **service** methods (the use case), not on repositories or controllers.
- `isolation` (`READ_COMMITTED`, `REPEATABLE_READ`, `SERIALIZABLE`) controls what concurrent transactions see; see the SQL transactions notes.
- A transaction ends and the **session closes** when the outermost transactional method returns: that is the moment lazy loading stops working (section 6).

---

## 11. Locking: Optimistic and Pessimistic

Two users edit the same row. Without protection, the second save silently overwrites the first (the **lost update**).

### Optimistic locking with `@Version`

Assume conflicts are rare: do not lock, **detect** them. `@Version private long version;` adds a column; every `UPDATE` becomes

```sql
update book set title=?, version=? where id=? and version=?
```

If another transaction already changed the row, `version` no longer matches, **zero rows** are updated and Spring throws `ObjectOptimisticLockingFailureException` (verified):

```java
// both users open the book at version 0
userA.setTitle("Changed by A");  save(userA);        // A saves first: version becomes 1
userB.setTitle("Changed by B");  save(userB);        // B still holds version 0 -> ObjectOptimisticLockingFailureException
// the stored title is still "Changed by A": B must reload and re-apply instead of overwriting
```

For REST APIs return the version to the client (`ETag`) and reject updates that carry an old one (`409 Conflict` or `412 Precondition Failed`). Best for web applications, where a user may take minutes between reading and saving.

### Pessimistic locking

Assume conflicts are likely: **lock the row** while you work.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select b from Book b where b.id = :id")
Optional<Book> findForUpdate(@Param("id") Long id);
```

```sql
select b1_0.id,... from book b1_0 where b1_0.id=? for update        -- verified: FOR UPDATE
```

Other transactions that try to lock or modify the row **wait** until this transaction ends. Use it for short, high-contention critical sections (stock decrement, seat booking), keep the transaction tiny, always lock rows in the **same order** (avoid deadlocks), and set a timeout (`jakarta.persistence.lock.timeout`).

| | Optimistic | Pessimistic |
|---|---|---|
| Mechanism | version check at update | database row lock |
| Blocks others | no | yes |
| On conflict | exception, retry or tell the user | waits, or times out / deadlock |
| Good for | low contention, user-edited forms | high contention, short critical sections |

A third option for counters: an atomic update (`update stock set qty = qty - 1 where id = ? and qty > 0`), no locks needed (see the SQL transaction notes).

---

## 12. Identifiers, Batching and Performance

### `IDENTITY` versus `SEQUENCE`

| Strategy | How the id is made | Batching inserts |
|---|---|---|
| `IDENTITY` | the **database** assigns it during the `INSERT` (auto-increment column) | **disabled**: Hibernate must insert each row immediately to learn its id |
| `SEQUENCE` | Hibernate asks a sequence for a block of ids (`allocationSize` 50) and assigns ids itself | **works** |

Verified with 100 new rows and `jdbc.batch_size=20`:

| Entity | Insert statements prepared |
|---|---|
| `Tag` (IDENTITY) | **100** (one per row) |
| `AuditLog` (SEQUENCE, allocationSize 50) | **1** (executed in JDBC batches) |

For tables that receive bulk inserts prefer `SEQUENCE` (PostgreSQL, Oracle). MySQL has no sequences, so for bulk loads use JDBC batch inserts (`JdbcTemplate.batchUpdate`) or `rewriteBatchedStatements=true` with care.

Remember `@SequenceGenerator(allocationSize = 50)` must match the database sequence's `INCREMENT BY`, or ids collide.

### A performance checklist

| Technique | Why |
|---|---|
| **Count SQL statements** in tests (`StatementInspector`, `Statistics`) | the only way to see N+1 |
| `LAZY` everywhere, fetch with `join fetch` / entity graphs / projections where needed | load only what the use case needs |
| Projections for read-only lists | no entities to track |
| Paginate every list | never `findAll()` on a big table |
| `hibernate.jdbc.batch_size` + `order_inserts` / `order_updates` + SEQUENCE | fewer round trips for bulk writes |
| Short transactions, `open-in-view=false` | do not hold connections |
| Index every foreign key and every column you filter or sort on | JPA does not create FK indexes on all databases |
| Use `exists` / `count` queries, not `findAll().size()` | do not load rows to count them |
| For large imports: `flush()` + `clear()` every few hundred rows | the persistence context otherwise grows until out of memory |
| Second-level cache (Ehcache, Caffeine through JCache) | only for read-mostly reference data, with care (stale data, clustering) |
| Query cache | rarely worth it |
| Connection pool sized sensibly (HikariCP, default 10) | more threads than connections just queue |

A very common 10x win is just: **find the N+1, replace with a fetch join or a projection**.

---

## 13. `equals`, `hashCode`, `toString` and DTOs

### `equals` and `hashCode`

Entities live in `Set`s and `Map`s (`Set<Tag>`), so `equals`/`hashCode` matter. The trap: an object's `id` is `null` until it is saved, so a `hashCode` computed from the id **changes** when the object is persisted, and an entity already in a `HashSet` is then lost.

| Strategy | Notes |
|---|---|
| Default (identity) | fine if you never compare detached copies or mix instances from different sessions |
| **Natural/business key** (a unique `name`, `isbn`) | `Tag` uses `Objects.equals(name, ...)` and `hash(name)`: stable and correct for lookup-style entities |
| **Database id for `equals`, constant `hashCode`** | `Book`: `equals` compares non-null ids, `hashCode` is `getClass().hashCode()`; works across the lifecycle (verified: a `Book` added to a `Set` **before** persisting is still found afterwards) |
| Lombok `@Data` / `@EqualsAndHashCode` on entities | **dangerous**: includes every field and relationship, triggering lazy loads, recursion and changing hashes; avoid on entities |

Compare class with `instanceof`, not `getClass()`, because Hibernate may hand you a **proxy subclass**.

### `toString`

Do **not** include lazy collections or back-references (`Author.toString` printing `books`, each printing its author...): infinite recursion and surprise queries. Print the id and a few simple fields.

### Do not expose entities from controllers

| Problem | Effect |
|---|---|
| Serializing a lazy proxy | `LazyInitializationException` or accidental loading of everything |
| Bidirectional relationships | infinite JSON recursion |
| Clients can send fields you did not intend | **mass assignment** (`isAdmin`, `version`, `price`) |
| Changes to the schema change the API | tight coupling |

Return **DTOs** (records) built in the service from projections or entities, and accept request DTOs that you map explicitly. (See the REST notes for DTO design.)

---

## 14. Schema Management

| `spring.jpa.hibernate.ddl-auto` | Does | Use |
|---|---|---|
| `none` | nothing | **production** with migrations |
| `validate` | checks entities against the schema, fails on mismatch | production safety net |
| `update` | adds missing tables and columns | quick dev only; never drops or renames; unsafe in production |
| `create` | drops and recreates at start | tests, demos |
| `create-drop` | creates at start, drops at shutdown | tests (used here) |

In real projects the schema is managed by **migrations** (Flyway or Liquibase): versioned SQL files that run in order and are reviewed like code. Hibernate then runs with `validate` (or `none`). Never let `update` alter a production database.

To see what Hibernate would generate (to start a first migration): set `jakarta.persistence.schema-generation.scripts.action=create` together with `jakarta.persistence.schema-generation.scripts.create-target=schema.sql` **and** keep `jakarta.persistence.schema-generation.database.action` set as well, otherwise Hibernate may skip creating the tables (this broke the example project until the properties were removed again).

---

## 15. Testing JPA Code

```java
@DataJpaTest                                          // Boot 4: org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
class PersistenceTest {
    @Autowired EntityManager em;
    @Autowired AuthorRepository authors;
    ...
}
```

- `@DataJpaTest` starts **only** the JPA slice with an in-memory database (H2) and wraps **each test in a transaction rolled back at the end**.
- Because the whole test is one transaction (one persistence context), reads come from memory: call **`em.flush(); em.clear();`** after arranging data so the next read really goes to the database (the `seed()` method does this).
- To test transaction behaviour (commit, rollback, `REQUIRES_NEW`, lazy loading after the transaction) use `@SpringBootTest` **without** `@Transactional` on the test, and `TransactionTemplate` for explicit transactions: `TransactionTest` does this.
- Add `@AutoConfigureTestDatabase(replace = NONE)` plus **Testcontainers** to test against the real database engine (PostgreSQL, MySQL): H2 does not behave exactly like them (identifiers, `enum` columns, JSON, locking, SQL dialect).
- Assert **statement counts** for every query that matters, so an N+1 regression fails the build.

---

## 16. Common Mistakes

| Mistake | Why | Fix |
|---|---|---|
| Not looking at the SQL | N+1 and giant queries stay hidden | show-sql in dev, count statements in tests |
| N+1 from `getXxx()` in a loop | one query per parent | `join fetch`, `@EntityGraph`, batch size, projection |
| `EAGER` fetching "to fix" lazy errors | loads huge graphs everywhere | fetch in the query that needs it |
| Returning entities from controllers | lazy errors, recursion, mass assignment | DTOs |
| Open Session In View left on | hides lazy loading, holds connections | `spring.jpa.open-in-view=false` |
| `@ManyToOne` left `EAGER` (the default) | extra joins and loads | `fetch = LAZY` |
| Setting only the inverse side of a relationship | nothing is written (or a NOT NULL error) | helper methods that set both sides |
| `cascade = ALL` / `REMOVE` on `@ManyToMany` or child-to-parent | deleting shared rows | cascade only parent to owned children |
| `@ManyToMany` with a `List` | remove deletes and re-inserts all links | use a `Set` |
| `@Enumerated` default `ORDINAL` | reordering constants corrupts data | `EnumType.STRING` |
| `IDENTITY` ids with bulk inserts | no JDBC batching | `SEQUENCE` (and batch settings) |
| Bulk `@Modifying` update without `clearAutomatically` | stale entities in memory | `clearAutomatically = true` |
| Checked exception in `@Transactional` and expecting rollback | it commits | `rollbackFor` |
| Calling a `@Transactional` method on `this` | no proxy, no transaction behaviour | call through another bean |
| `findAll()` without paging | loads the whole table | `Pageable` |
| `Page<T>` where `Slice<T>` is enough | an extra count query | `Slice` |
| Lombok `@Data` on entities | recursion, lazy loads, wrong `hashCode` | write `equals`/`hashCode`/`toString` yourself or use `@Getter` only |
| `@Transactional` on a `private` or `final` method / in a class that is not a Spring bean | silently ignored | public methods on beans |
| `ddl-auto=update` in production | uncontrolled schema changes | Flyway/Liquibase and `validate` |
| Long transactions around remote calls | held connections and locks, timeouts | keep transactions short |
| Loading an entity just to change one column | a SELECT plus an UPDATE of every column | `@Modifying` query or `@DynamicUpdate` where it matters |
| String-built JPQL / native SQL | injection | named parameters |
| Testing only with H2 | production differences | Testcontainers for critical queries |

---

## 17. Output Prediction Puzzles

Every answer is asserted by a test in `PersistenceTest` or `TransactionTest`.

**Puzzle 1: first-level cache.** Two `findById` calls with the same id in one transaction: how many SELECTs, and are the objects the same?

**Puzzle 2: N+1.** `authors.findAll()` for 3 authors, then `getBooks().size()` for each: how many SELECTs? With `join fetch`?

**Puzzle 3: changed but never saved.**

```java
Book book = books.findById(id).get();
book.setTitle("New");
// no save(); the transaction commits
```
What happens, and what is `version` afterwards?

**Puzzle 4: a detached change.**

```java
em.persist(author); em.flush(); em.detach(author);
author.setName("Renamed");
em.flush();
```
Is an UPDATE sent? What does `em.merge(author)` return?

**Puzzle 5: checked exception.**

```java
@Transactional
void save() throws IOException { repo.save(new Author("X")); throw new IOException("disk full"); }
```
Is the author in the database afterwards? And with `rollbackFor = IOException.class`?

**Puzzle 6: REQUIRES_NEW.** The outer transaction saves an author, calls another bean's `REQUIRES_NEW` method to write an audit row, then throws a `RuntimeException`. What is in the database? What if the audit method is on the same class (`this.writeAudit(...)`)?

**Puzzle 7: orphan removal.**

```java
asha.removeBook(asha.getBooks().get(0));
em.flush();
```
What happens to the removed book's row?

**Puzzle 8: bulk update.** A `Book` is loaded (status `DRAFT`), then `@Modifying` `markShortBooks(OUT_OF_PRINT, 400)` runs. What does `book.getStatus()` return? After `em.clear()` and reloading?

**Puzzle 9: ids and batching.** Insert 100 `Tag`s (IDENTITY) and 100 `AuditLog`s (SEQUENCE, `batch_size=20`). How many `insert` statements does Hibernate prepare for each?

**Puzzle 10: lost update.** Users A and B load the same `@Version`ed book (version 0). A saves, then B saves. What happens?

**Answers**

1. **One SELECT, and yes: the same object** (`isSameAs`). The persistence context returns the managed instance without querying again.
2. **4 SELECTs** (1 + 3), and **1 SELECT** with `join fetch` (or an entity graph).
3. **An `UPDATE` is sent at commit**, even without `save`: dirty checking compares the snapshot. `version` goes from `0` to `1`.
4. **No UPDATE:** a detached entity is not tracked. `merge` returns a **new managed instance** (not the same object as `author`) carrying the renamed state.
5. **Yes, it is committed** (a checked exception does not roll back by default). With `rollbackFor = IOException.class` it is **not** saved.
6. **The author is rolled back, the audit row survives** (a separate transaction committed first). With `this.writeAudit(...)` the proxy is bypassed: **both are lost**.
7. **The row is deleted** (`orphanRemoval = true`); the count of books drops from 9 to 8.
8. **`DRAFT`** (the entity in memory is stale; bulk updates bypass the persistence context). After `clear()` and reload: **`OUT_OF_PRINT`**.
9. **100** for `Tag` (IDENTITY disables batching) and **1** for `AuditLog` (one prepared statement, executed in batches).
10. **B gets `ObjectOptimisticLockingFailureException`**, because B's `UPDATE ... where version = 0` matches no row. The stored title is A's.

---

## 18. Interview Questions

**1. What is the difference between JPA, Hibernate and Spring Data JPA?**
JPA is the specification (annotations, `EntityManager`); Hibernate is an implementation; Spring Data JPA generates repository implementations on top of JPA.

**2. What is the persistence context?**
The set of entities managed by one `EntityManager`/transaction: a first-level cache (one instance per row), with dirty checking and flushing of changes.

**3. What are the entity states?**
New (transient), managed (persistent), detached, removed.

**4. `persist` vs `merge` vs `save`?**
`persist` makes a new entity managed; `merge` copies state from a detached/new object onto a managed instance and returns it; Spring Data's `save` calls `persist` for a new entity and `merge` otherwise.

**5. How can an entity be updated without calling `save`?**
Dirty checking: a managed entity changed inside a transaction is written at flush/commit.

**6. What is the N+1 problem and how do you fix it?**
One query for the parents plus one per parent for lazy children. Fix with `join fetch`, `@EntityGraph`, batch fetching, or projections; detect it by counting statements.

**7. `LAZY` vs `EAGER`?**
Lazy loads an association on first access (needs an open session); eager loads it together with the owner. Defaults: `@ManyToOne`/`@OneToOne` eager, `@OneToMany`/`@ManyToMany` lazy. Prefer lazy and fetch explicitly.

**8. What causes `LazyInitializationException`?**
Accessing a lazy association after the session closed (outside the transaction). Fix by fetching in the query, working inside the transaction, or using DTOs.

**9. What is Open Session In View and why disable it?**
It keeps the session open through the web request so lazy loading works in controllers. It hides N+1 and holds connections; disable it and fetch deliberately.

**10. Owning side vs inverse side?**
The owning side (without `mappedBy`, holding the foreign key) is what Hibernate writes; the inverse side (`mappedBy`) only mirrors it. Keep both in sync with helper methods.

**11. What do `cascade` and `orphanRemoval` do?**
`cascade` propagates operations (persist, remove...) from parent to children; `orphanRemoval` deletes children removed from the parent's collection.

**12. Which exceptions roll back a `@Transactional` method?**
Unchecked exceptions by default; checked ones do not unless `rollbackFor` says so.

**13. What does `REQUIRES_NEW` do, and what is the self-invocation trap?**
Runs in a separate transaction that commits independently. Calling the method on `this` bypasses the proxy, so no new transaction starts.

**14. Optimistic vs pessimistic locking?**
Optimistic detects conflicts with a `@Version` column at update time (exception on conflict); pessimistic locks the row (`SELECT ... FOR UPDATE`) so others wait.

**15. Why do `IDENTITY` ids hinder batch inserts?**
Hibernate needs the generated id right after each insert, so it cannot defer and batch inserts. `SEQUENCE` ids are allocated in blocks in advance, so inserts can be batched.

**16. Why should you not use Lombok `@Data` on entities?**
Generated `equals`/`hashCode`/`toString` include all fields and relationships, causing lazy loading, infinite recursion and unstable hash codes.

**17. When would you use a projection instead of an entity?**
For read-only views of part of the data: one lean query, no persistence-context overhead, no lazy-loading surprises.

**18. What does `@Modifying` do and what is its catch?**
It marks an update/delete query. It bypasses the persistence context and `@Version`, so loaded entities become stale: use `clearAutomatically = true`.

**19. How do you handle pagination with a fetch join?**
In Hibernate 6 and earlier the limit was applied in memory (warning `HHH90003004`), so page the ids first then fetch; Hibernate 7 pages the root rows in the database.

**20. How would you test that a query does not cause N+1?**
Register a `StatementInspector` (or use Hibernate `Statistics`) and assert the number of SQL statements for the call.

---

## 19. Cheat Sheet

| Topic | Remember |
|---|---|
| Stack | Spring Data JPA -> JPA -> Hibernate -> JDBC |
| Persistence context | 1 instance per row; dirty checking; first-level cache; flushed at commit |
| States | transient, managed, detached, removed |
| `save` | `persist` if new, else `merge` (extra SELECT) |
| Relationships | the side **without** `mappedBy` owns the FK; use helper methods |
| Fetch defaults | ToOne EAGER (set LAZY), ToMany LAZY |
| Cascade | parent to owned children only; `orphanRemoval` for removed children |
| Many-to-many | `Set`; real entity if the link has data |
| Lazy errors | fetch in the query, DTOs, `open-in-view=false` |
| N+1 | `join fetch` / `@EntityGraph` / `default_batch_fetch_size` / projections; **count statements** |
| Paging | `Page` costs a count query; `Slice` does not; keyset for deep pages; fetch join + paging fixed in Hibernate 7 |
| Projections | record or interface; entities are for writing |
| Bulk update | bypasses the context: `clearAutomatically = true` |
| Transactions | unchecked rolls back, checked commits; `REQUIRES_NEW`; no self-calls; keep short |
| Locking | `@Version` optimistic; `PESSIMISTIC_WRITE` = `FOR UPDATE` |
| Ids | `IDENTITY` = no batching; `SEQUENCE` batches |
| Equality | natural key, or id-equals + constant hash; never Lombok `@Data` |
| API | DTOs, never entities |
| Schema | migrations (Flyway); `validate` in production |
| Tests | `@DataJpaTest` + `flush`/`clear`; `@SpringBootTest` for transactions; Testcontainers; assert SQL counts |

---

## 20. Practice Tasks

1. **Hunt the N+1.** Add a `GET /authors` endpoint returning authors with their book titles. Write a test that fails if more than 2 SQL statements run. Fix it three ways (join fetch, entity graph, projection) and compare the SQL.
2. **Batch fetching.** Remove the join fetch, set `default_batch_fetch_size`, and show the count of statements for 25 authors. What changes at sizes 5, 10 and 50?
3. **Paging.** Implement `Page<AuthorSummary>` with a record projection and a stable sort. Then implement keyset paging for the same list and compare the SQL for page 1 and page 1,000 on 100,000 rows.
4. **Lazy exception.** Reproduce `LazyInitializationException` in a controller with `open-in-view=false`, then fix it by returning a DTO from a `@Transactional(readOnly = true)` service method.
5. **Many-to-many with data.** Replace `Book.tags` with an entity `BookTag(book, tag, addedAt)`; compare the SQL for adding and removing a tag with the `Set<Tag>` version.
6. **Locking.** Add a `stock` field to `Book` and write a test with two threads that both decrement it: first showing the lost update, then fixing it with `@Version` (retry on conflict), then with an atomic `update ... where stock > 0`.
7. **Transaction traps.** Write tests for: a caught exception inside `@Transactional` (does it roll back?), `readOnly = true` with a write, a `REQUIRES_NEW` called from the same class, and a checked exception with and without `rollbackFor`.
8. **Batching.** Import 10,000 rows with `IDENTITY` and with `SEQUENCE`; compare times and statement counts, and add `flush()`/`clear()` every 500 rows to keep memory flat.
9. **Real database.** Run `PersistenceTest` against PostgreSQL with Testcontainers (`@AutoConfigureTestDatabase(replace = NONE)`) and note any test whose SQL or behaviour differs from H2.
10. **Migration.** Generate the DDL for the model, turn it into `V1__init.sql` for Flyway, set `ddl-auto=validate`, and add `V2__add_isbn.sql` for a new column.

*End of notes.*

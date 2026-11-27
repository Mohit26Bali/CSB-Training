-- Challenge Arena: the JDBC topic and its questions (for the JDBC Lab). Every question has exactly one correct option.
-- Written for Java 17, JDBC 4.3, MySQL 8.4 with Connector/J, HikariCP and Spring Framework 6 / Spring Boot 3.5.

INSERT INTO topics (name, slug, description) VALUES
    ('JDBC and Spring JDBC', 'jdbc', 'Connections, statements, transactions, pooling, JdbcTemplate and how JPA builds on JDBC.');

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'EASY', 'What is JDBC?',
 'JDBC is the standard Java API for talking to relational databases: interfaces such as Connection, PreparedStatement and ResultSet that a database-specific driver implements. It is not a database and not a query language, and it predates and sits below Spring and JPA.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'A database server written in Java', FALSE, 0), (@q, 'A Java API (in java.sql and javax.sql) for executing SQL against relational databases through a driver', TRUE, 1), (@q, 'A query language that replaces SQL for Java programs', FALSE, 2), (@q, 'A Spring module that generates SQL from entity classes', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'EASY', 'What does a JDBC driver do?',
 'The application codes against the java.sql interfaces; the driver (for MySQL, Connector/J) implements them and speaks MySQL''s wire protocol. The database stores and executes; object-to-table conversion is the job of an ORM such as Hibernate, which itself uses a driver.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'It implements the JDBC interfaces for one database product and translates the calls into that database''s own protocol', TRUE, 0), (@q, 'It stores the data of the application on disk', FALSE, 1), (@q, 'It converts Java objects to tables automatically', FALSE, 2), (@q, 'It decides which SQL statements are allowed to run', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'Why do applications normally obtain connections from a DataSource instead of calling DriverManager.getConnection directly?',
 'DriverManager asks the driver for a brand-new physical connection every time. A DataSource (for example HikariCP in Spring Boot) can hand out pooled connections, so the expensive connection is created once and reused. DriverManager still exists and works; the point is reuse and configuration, not result caching.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'DriverManager cannot connect to MySQL', FALSE, 0), (@q, 'A DataSource makes every query run faster by caching the results', FALSE, 1), (@q, 'DriverManager is removed in Java 17', FALSE, 2), (@q, 'A DataSource can pool and reuse connections and keeps URL and credentials in configuration, while DriverManager opens a new physical connection each call', TRUE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'EASY', 'What does a java.sql.Connection represent?',
 'A Connection is a handle to a database session: it owns the transaction (auto-commit, commit, rollback, isolation) and creates Statement and PreparedStatement objects. With a pool the Connection object the application holds is a proxy over a long-lived physical connection.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'A single SQL statement waiting to be executed', FALSE, 0), (@q, 'The result of a query', FALSE, 1), (@q, 'A session with the database, which carries its own transaction state and creates statements', TRUE, 2), (@q, 'The database server itself', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'Why does binding a value with PreparedStatement.setString protect against SQL injection?',
 'With a placeholder the query structure is decided before the value is bound, and the driver only ever treats the bound value as a value (escaping or transmitting it separately). Stripping quotes or blocking keywords is not how it works, and concatenating input into the SQL text loses the protection.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'The driver removes all quote characters from the value', FALSE, 0), (@q, 'The SQL structure is fixed with a placeholder and the value is passed as data, so it cannot change the meaning of the statement', TRUE, 1), (@q, 'MySQL refuses any statement that contains the word OR', FALSE, 2), (@q, 'setString encrypts the value before it is sent', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'EASY', 'Where is a ResultSet cursor positioned right after executeQuery returns?',
 'A ResultSet starts before the first row. next() moves to the next row and returns false when there are none left. Reading a column without calling next() first throws an SQLException.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'Before the first row, so next() must be called before reading', TRUE, 0), (@q, 'On the first row, ready to read', FALSE, 1), (@q, 'After the last row', FALSE, 2), (@q, 'It has no position until getRow() is called', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'Which method should run an INSERT, UPDATE or DELETE and returns the number of affected rows?',
 'executeUpdate is for statements that change data; it returns an int row count. executeQuery is for statements that produce rows and returns a ResultSet. commit ends a transaction and does not execute SQL.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'Statement.executeQuery', FALSE, 0), (@q, 'ResultSet.updateRow', FALSE, 1), (@q, 'Connection.commit', FALSE, 2), (@q, 'Statement.executeUpdate (or PreparedStatement.executeUpdate)', TRUE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'What does try-with-resources do for a Connection, PreparedStatement and ResultSet declared in its header?',
 'Resources declared in the header are closed in reverse order when the block exits normally or by exception. That is how JDBC code avoids leaking connections. It does not commit or retry anything.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'It commits the transaction when the block ends', FALSE, 0), (@q, 'It keeps them open until the garbage collector runs', FALSE, 1), (@q, 'It closes them automatically in reverse order of declaration, even when an exception is thrown', TRUE, 2), (@q, 'It retries the statement when it fails', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'What happens to a ResultSet when the Statement that produced it is closed?',
 'The JDBC specification says closing a Statement releases its current ResultSet. Relying on that is poor style: close what you open, in reverse order, which try-with-resources does for you.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'It stays usable until the connection is closed', FALSE, 0), (@q, 'It is closed too', TRUE, 1), (@q, 'It is converted to a CachedRowSet', FALSE, 2), (@q, 'Nothing: the two are independent', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'What does PreparedStatement.executeBatch return?',
 'executeBatch sends the parameter sets added with addBatch and returns an int[] whose elements are the update counts (or Statement.SUCCESS_NO_INFO). It does not return rows.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'An int array with one update count per batched parameter set', TRUE, 0), (@q, 'A single ResultSet containing all inserted rows', FALSE, 1), (@q, 'The total number of batches sent', FALSE, 2), (@q, 'Nothing: it returns void', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'HARD', 'With MySQL Connector/J''s default settings, what does executeBatch do with five batched INSERTs?',
 'By default the driver sends each batched statement separately (so MySQL counts five INSERTs). The option rewriteBatchedStatements=true makes it rewrite them into fewer statements. A batch saves round trips only when the driver is configured for it, which is why the observed statement count matters.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'It always combines them into one multi-row INSERT', FALSE, 0), (@q, 'It sends them in one network packet that MySQL counts as a single statement', FALSE, 1), (@q, 'It waits until commit() before sending any of them', FALSE, 2), (@q, 'The driver still sends five separate INSERT statements; only rewriteBatchedStatements=true lets it combine them', TRUE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'EASY', 'What is the default auto-commit mode of a new JDBC connection?',
 'JDBC connections start with auto-commit true, so every statement is committed as soon as it finishes. Transactions that span several statements require setAutoCommit(false) (or a framework that does it) followed by commit() or rollback().',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'Off: nothing is committed until commit() is called', FALSE, 0), (@q, 'It depends on the isolation level', FALSE, 1), (@q, 'On: each statement is its own transaction and is committed when it completes', TRUE, 2), (@q, 'On, but only for SELECT statements', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'Auto-commit is off and executeUpdate returned normally for two UPDATEs. What is the state of those changes?',
 'A normal return from executeUpdate only means MySQL applied the statement inside the transaction. Durability and visibility to others begin at commit(). Until then rollback() discards both changes.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'They are committed, because executeUpdate returned', FALSE, 0), (@q, 'They exist only inside the open transaction: they are not committed and, at MySQL''s default isolation, other connections cannot see them', TRUE, 1), (@q, 'They are committed after one second', FALSE, 2), (@q, 'They are visible to every connection but can be undone', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'HARD', 'In a MySQL InnoDB transaction the second of two statements fails with a foreign key violation. What happens to the first statement?',
 'InnoDB rolls back only the failed statement for errors such as constraint violations; the transaction remains open and the earlier changes are still pending. Application code (or @Transactional) has to decide to roll back. Deadlocks are different: they roll back the whole transaction.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'It stays pending in the open transaction; only the failing statement is undone, so the code must call rollback() (or commit) itself', TRUE, 0), (@q, 'It is rolled back automatically together with the second', FALSE, 1), (@q, 'It is committed automatically', FALSE, 2), (@q, 'MySQL closes the connection', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'What does rolling back to a Savepoint do?',
 'A savepoint marks a point inside a transaction. Rolling back to it discards only later changes; the transaction continues and still needs commit() or rollback(). That makes it useful for partial recovery inside a larger unit of work.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'It commits the work done before the savepoint', FALSE, 0), (@q, 'It ends the transaction and discards everything', FALSE, 1), (@q, 'It closes the connection', FALSE, 2), (@q, 'It undoes the work done after the savepoint and leaves the transaction open with the earlier work still pending', TRUE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'Transaction A reads a row twice under REPEATABLE READ while transaction B updates and commits that row between the reads. What does A see?',
 'REPEATABLE READ gives a transaction a consistent snapshot, so repeated reads return the same data even after other transactions commit. READ COMMITTED would show the new committed value on the second read; only READ UNCOMMITTED could show uncommitted data.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'The old value first and the committed new value second', FALSE, 0), (@q, 'An error, because the row changed', FALSE, 1), (@q, 'The same value both times, from the snapshot taken at its first read', TRUE, 2), (@q, 'The uncommitted value if B had not yet committed', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'Which anomaly can still happen under READ COMMITTED but not under REPEATABLE READ?',
 'READ COMMITTED sees only committed data, so dirty reads are impossible, but each statement sees the latest committed data, so a second read in the same transaction may differ. REPEATABLE READ pins a snapshot and prevents that.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'A dirty read of another transaction''s uncommitted change', FALSE, 0), (@q, 'A non-repeatable read: the same query returns different committed data later in the same transaction', TRUE, 1), (@q, 'A lost commit', FALSE, 2), (@q, 'A duplicate primary key', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'HARD', 'What is the default transaction isolation level of MySQL''s InnoDB?',
 'InnoDB defaults to REPEATABLE READ (unlike several other databases that default to READ COMMITTED). It can be read with SELECT @@transaction_isolation and changed per session or transaction.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'REPEATABLE READ', TRUE, 0), (@q, 'READ COMMITTED', FALSE, 1), (@q, 'READ UNCOMMITTED', FALSE, 2), (@q, 'SERIALIZABLE', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'EASY', 'What problem does a connection pool solve?',
 'Creating a connection involves network and authentication work. A pool keeps a limited set of open connections and lends them to callers, bounding how many sessions the application can hold. It does not change how fast MySQL runs a statement.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'It makes every SQL statement execute faster inside MySQL', FALSE, 0), (@q, 'It encrypts the traffic to the database', FALSE, 1), (@q, 'It removes the need for transactions', FALSE, 2), (@q, 'Opening a physical database connection is expensive, so a pool creates connections once and lends them out repeatedly', TRUE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'What does close() do on a connection obtained from a HikariCP pool?',
 'The object the application holds is a proxy. close() marks the connection idle and returns it to the pool (resetting auto-commit and similar state), so the next caller can use the same physical session.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'It always closes the physical connection to MySQL', FALSE, 0), (@q, 'It commits the transaction and destroys the pool', FALSE, 1), (@q, 'It returns the connection to the pool; the physical database session normally stays open for reuse', TRUE, 2), (@q, 'It has no effect', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'All connections of a HikariCP pool are in use and another thread calls getConnection(). What happens?',
 'The pool never exceeds its maximum. A waiting thread gets a connection as soon as one is returned, or an exception after the configured connection timeout. Leaked or long-held connections are the usual cause.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'The pool creates connections beyond maximumPoolSize', FALSE, 0), (@q, 'The call waits up to connectionTimeout and then throws an SQLTransientConnectionException', TRUE, 1), (@q, 'The call returns null', FALSE, 2), (@q, 'The oldest borrowed connection is closed to make room', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'EASY', 'What does JdbcTemplate take care of for you?',
 'JdbcTemplate removes the resource-handling boilerplate: connection, statement and ResultSet lifecycle and exception translation. You still write the SQL and decide how rows become objects.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'Acquiring and releasing the connection, preparing and executing the statement, closing resources and translating SQLException', TRUE, 0), (@q, 'Writing the SQL for you', FALSE, 1), (@q, 'Mapping tables to entity classes', FALSE, 2), (@q, 'Choosing the isolation level for every query', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'What is the difference between a RowMapper and a ResultSetExtractor?',
 'RowMapper<T> is called for each row and returns one object per row. ResultSetExtractor<T> is called once with the ResultSet and returns whatever it builds (for example a Map or an aggregate). Both are used with JdbcTemplate.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'A RowMapper writes rows and a ResultSetExtractor reads them', FALSE, 0), (@q, 'They are the same interface under two names', FALSE, 1), (@q, 'A RowMapper works only with NamedParameterJdbcTemplate', FALSE, 2), (@q, 'A RowMapper maps one row and is called once per row; a ResultSetExtractor receives the whole ResultSet and processes it in one call', TRUE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'What does NamedParameterJdbcTemplate do with a SQL text such as ... where name = :name?',
 'JDBC itself only knows positional ? placeholders. NamedParameterJdbcTemplate parses the names, expands repeated names into several ? and binds the values in the right order, so the driver sees ordinary placeholders.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'It sends :name to MySQL, which understands named parameters', FALSE, 0), (@q, 'It concatenates the value into the SQL text', FALSE, 1), (@q, 'It rewrites :name to a ? placeholder and binds the supplied value by position before the statement reaches JDBC', TRUE, 2), (@q, 'It turns the query into a stored procedure', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'Why does Spring translate SQLException into DataAccessException subclasses such as DuplicateKeyException?',
 'The translator maps vendor error codes and SQL states to a database-independent hierarchy. The original SQLException is kept as the cause, so the vendor code is still available when needed.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'Because SQLException cannot be thrown from a Spring bean', FALSE, 0), (@q, 'So code can handle failure categories with unchecked exceptions without depending on one database''s error codes', TRUE, 1), (@q, 'To hide the original error completely', FALSE, 2), (@q, 'Because MySQL does not report error codes', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'Several JdbcTemplate calls run inside one TransactionTemplate callback using a DataSourceTransactionManager. How many connections do they use?',
 'The transaction manager obtains one connection, turns auto-commit off, binds it to the current thread and commits or rolls back at the end. JdbcTemplate looks for a bound connection first, which is how several statements become one transaction.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'One: the manager binds a connection to the thread and every call in the callback reuses it', TRUE, 0), (@q, 'One per call', FALSE, 1), (@q, 'One per table', FALSE, 2), (@q, 'None: they use the pool directly without a connection', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'HARD', 'How does Hibernate send SQL to MySQL?',
 'Hibernate is built on JDBC: it prepares statements, binds parameters and reads ResultSets exactly like hand-written JDBC, on a pooled connection. JPA changes who writes the SQL and maps the rows; it does not remove connections, transactions or the driver.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'Through its own native MySQL protocol implementation, bypassing JDBC', FALSE, 0), (@q, 'By generating stored procedures that MySQL runs', FALSE, 1), (@q, 'Through JdbcTemplate only', FALSE, 2), (@q, 'Through the JDBC API on a java.sql.Connection from the DataSource, using the MySQL JDBC driver', TRUE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'HARD', 'JdbcTemplate and JPA code run in the same method under a JpaTransactionManager and use the same DataSource. What can be expected?',
 'JpaTransactionManager exposes its JDBC connection to Spring JDBC code that uses the same DataSource, so both run in one transaction. Hibernate only sends pending changes at flush, so a JdbcTemplate query can miss changes that have not been flushed; sharing needs deliberate configuration.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'They always use separate connections and cannot see each other''s uncommitted changes', FALSE, 0), (@q, 'The code cannot be mixed in one application', FALSE, 1), (@q, 'They can share one transaction and one database connection, so JdbcTemplate sees changes Hibernate has already flushed in that transaction', TRUE, 2), (@q, 'JdbcTemplate automatically flushes the persistence context', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'HARD', 'Which statement about JDBC, Spring JDBC and JPA is correct?',
 'All three end as SQL sent through a JDBC driver. Differences come from how many statements run, how rows are mapped and how transactions are used, not from the label. A tiny single-read test says nothing about general performance.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jdbc';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'JDBC is always faster than JPA', FALSE, 0), (@q, 'Neither is universally faster; the cost depends on the SQL, round trips and mapping, and JPA can generate the same SQL as hand-written JDBC', TRUE, 1), (@q, 'JPA is always faster because it caches everything', FALSE, 2), (@q, 'Spring JDBC cannot run the same SQL as JDBC', FALSE, 3);

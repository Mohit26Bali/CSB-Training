/**
 * JDBC Lab: the module's authored content. Everything here is explanation or layout. Observations (SQL, rows, counters, pool
 * metrics, transaction states) never appear in this file: they come from LIVE runs on the server (POST /api/lab/jdbc/...).
 * The diagrams reuse the shared flow-diagram framework (FlowCanvas); their node and edge ids are checked against the server's
 * catalogue in the tests.
 */
import { cell } from '../schema/layout'

export const MODULE = Object.freeze({
  id: 'jdbc',
  title: 'JDBC Lab',
  topicSlug: 'jdbc', // Challenge Arena topic used by "Test yourself"
  summary:
    'Open real connections, run real SQL, commit and roll back with two sessions, exhaust a connection pool, and see what JdbcTemplate and Hibernate do on top of JDBC.',
})

/** One 5W1H per topic. Each is checked by validateExplain5W1H in the tests and again when rendered. */
export const TOPICS = [
  {
    id: 'fundamentals',
    title: 'JDBC fundamentals',
    group: 'fundamentals',
    explain: {
      what: 'JDBC (Java Database Connectivity) is the standard Java API for talking to relational databases. The API is a set of interfaces in java.sql and javax.sql (Connection, Statement, PreparedStatement, ResultSet, DataSource). A JDBC driver is a library that implements those interfaces for one database product; for MySQL it is Connector/J.',
      why: 'Java code should not depend on the wire protocol of one database. JDBC lets an application be written against stable interfaces while the driver handles the database-specific work. Everything else in this lab (Spring JDBC, JPA, Hibernate) is built on top of it.',
      who: [
        { name: 'The application', role: 'asks for a Connection and creates statements through the java.sql interfaces' },
        { name: 'DriverManager and DataSource', role: 'two ways to obtain a Connection: DriverManager opens a new physical one, a DataSource (HikariCP in SpringLab) usually pools them' },
        { name: 'The JDBC driver (MySQL Connector/J)', role: 'implements the interfaces and speaks MySQL\'s protocol' },
        { name: 'MySQL', role: 'runs the SQL; each Connection corresponds to one database session' },
      ],
      where: 'In the persistence layer of SpringLab, below repositories, JdbcTemplate and Hibernate. Spring Boot builds the DataSource from spring.datasource.* so application code rarely calls DriverManager at all.',
      when: 'Whenever the application needs the database: a connection is obtained, used for one or more statements and given back. The driver is found when DriverManager is first used, through the Java ServiceLoader.',
      how: [
        'The application asks the DataSource for a Connection.',
        'The Connection (a pool proxy over a driver connection) creates a statement.',
        'The driver turns the call into MySQL\'s protocol and sends it to the server.',
        'The result comes back through the driver as a ResultSet or an update count.',
        'close() hands a pooled connection back; with DriverManager it ends the session.',
      ],
    },
  },
  {
    id: 'statements',
    title: 'Statement, PreparedStatement and ResultSet',
    group: 'statements',
    explain: {
      what: 'A Statement sends the SQL text it is given. A PreparedStatement holds SQL with ? placeholders and binds values separately by position. A ResultSet is a cursor over the rows a query returned: it starts before the first row and next() moves it forward.',
      why: 'Building SQL by concatenating input lets the input change the meaning of the query (SQL injection). Binding keeps values as data. A ResultSet is also a resource that must be closed, which is why try-with-resources is the standard way to write JDBC code.',
      who: [
        { name: 'Statement', role: 'executes SQL text; executeQuery returns a ResultSet, executeUpdate a row count, execute tells you which one you got' },
        { name: 'PreparedStatement', role: 'parameterised SQL; the same statement can be executed again with other values' },
        { name: 'ResultSet', role: 'row cursor with typed getters by column label or 1-based index' },
        { name: 'MySQL Connector/J', role: 'by default prepares statements on the client (useServerPrepStmts=false) and escapes bound values' },
      ],
      where: 'In the code that talks to the database: hand-written JDBC, and underneath JdbcTemplate and Hibernate, which use the same objects.',
      when: 'Each time a query or an update is executed; the statement and result set live only for that call, the connection lives longer.',
      how: [
        'prepareStatement(sql) with placeholders (or createStatement()).',
        'setString/setLong bind the values by position.',
        'executeQuery/executeUpdate/execute sends the statement.',
        'next() walks the ResultSet and getters read the current row.',
        'The ResultSet, statement and connection are closed in reverse order.',
      ],
    },
  },
  {
    id: 'crud',
    title: 'CRUD with JDBC',
    group: 'crud',
    explain: {
      what: 'CRUD means create, read, update, delete: INSERT, SELECT, UPDATE and DELETE statements. executeUpdate returns the number of rows affected, getGeneratedKeys returns keys the database generated (AUTO_INCREMENT), and a batch sends many parameter sets with addBatch and executeBatch.',
      why: 'The row count is how code learns whether a statement did anything: an UPDATE or DELETE that matches nothing is not an error. Batches exist to reduce round trips, but only when the driver is configured to combine them.',
      who: [
        { name: 'PreparedStatement', role: 'runs each operation with bound parameters' },
        { name: 'ResultSet from getGeneratedKeys', role: 'returns the id MySQL assigned to the new row' },
        { name: 'The jdbc_lab_* tables', role: 'small isolated tables with primary keys, a foreign key and a unique key' },
        { name: 'MySQL session counters', role: 'Com_insert, Com_update, Com_delete and Com_select count what the server really received' },
      ],
      where: 'In any data-access code; the lab runs each operation on its own temporary rows and removes them afterwards.',
      when: 'On every write or read. With auto-commit on each statement commits by itself; a batch inside a transaction commits once.',
      how: [
        'INSERT with RETURN_GENERATED_KEYS gives the new id.',
        'SELECT by primary key returns zero or one row.',
        'UPDATE and DELETE return the affected row count, 0 when nothing matched.',
        'addBatch collects parameter sets and executeBatch returns one count per set.',
        'MySQL\'s own counters confirm how many statements arrived.',
      ],
    },
  },
  {
    id: 'transactions',
    title: 'JDBC transactions',
    group: 'transactions',
    explain: {
      what: 'A transaction groups statements into one unit of work that is committed or rolled back as a whole (ACID: atomicity, consistency, isolation, durability). A new JDBC connection is in auto-commit mode; setAutoCommit(false) starts explicit transactions ended by commit() or rollback(). A Savepoint marks a point inside a transaction, and the isolation level decides what concurrent transactions see.',
      why: 'Money moves in two statements; either both must happen or neither. A statement that returned normally is not committed: until commit() other sessions cannot see it and rollback() can still discard it. Isolation trades strictness for concurrency.',
      who: [
        { name: 'Connection A', role: 'does the work inside the transaction' },
        { name: 'Connection B', role: 'a second, separate session that only reads, to show what other sessions can see' },
        { name: 'MySQL InnoDB', role: 'keeps uncommitted changes private to their transaction and enforces foreign keys per statement' },
        { name: 'Transaction isolation level', role: 'REPEATABLE READ (MySQL\'s default), READ COMMITTED and READ UNCOMMITTED differ in what a second read sees' },
      ],
      where: 'On the Connection: transaction state belongs to the database session, not to the Java object that wraps it.',
      when: 'From setAutoCommit(false) (the transaction starts with the first statement) until commit() or rollback(). The lab uses a fixed order of operations on two connections, so no timing is involved.',
      how: [
        'A turns auto-commit off and runs its statements: they are pending.',
        'B reads: it does not see them (REPEATABLE READ and READ COMMITTED never show uncommitted data).',
        'commit() makes them permanent and visible; rollback() discards them all.',
        'A failing statement undoes only itself: the earlier ones stay pending until the code rolls back.',
        'rollback(savepoint) undoes only the later work and leaves the transaction open.',
      ],
    },
  },
  {
    id: 'pooling',
    title: 'Connection pooling and HikariCP',
    group: 'pooling',
    explain: {
      what: 'A connection pool keeps a limited set of open database connections and lends them to callers, so the expensive physical connection is created once and reused. HikariCP is the pool Spring Boot uses by default. Its main settings are maximumPoolSize, minimumIdle and connectionTimeout.',
      why: 'Opening a connection involves network and authentication work, and every session costs MySQL memory. A pool bounds the number of sessions and removes the opening cost from each request. The price is that a leaked or long-held connection starves other callers.',
      who: [
        { name: 'HikariCP (HikariDataSource)', role: 'the pool; hands out proxy connections and tracks active, idle, total and waiting counts' },
        { name: 'The borrowing thread', role: 'owns a connection until it calls close()' },
        { name: 'The lab pool', role: 'a small bounded pool created for ONE experiment run and closed afterwards; the application\'s own pool is only read' },
        { name: 'MySQL', role: 'sees the physical sessions, not the borrow and return calls' },
      ],
      where: 'Between the application and the driver. In SpringLab the DataSource bean is a HikariDataSource; JdbcTemplate and Hibernate both borrow from it.',
      when: 'On every getConnection() and close(). Pool exhaustion happens when all connections are borrowed and a further caller waits longer than connectionTimeout.',
      how: [
        'getConnection() lends an idle connection or, below the maximum, opens a new one.',
        'close() on the proxy returns the connection to the pool; the MySQL session stays open.',
        'The next borrower can receive the same physical session.',
        'When the maximum is reached the caller waits, then fails with SQLTransientConnectionException.',
        'Metrics (active, idle, total, waiting) are read from the pool\'s MXBean.',
      ],
    },
  },
  {
    id: 'spring',
    title: 'Spring JDBC',
    group: 'spring',
    explain: {
      what: 'Spring JDBC wraps JDBC in a template: JdbcTemplate (positional ?), NamedParameterJdbcTemplate (:names), RowMapper (one row to one object) and ResultSetExtractor (the whole ResultSet). It acquires and releases connections, closes resources and translates SQLException into the unchecked DataAccessException hierarchy. With a transaction manager, several template calls share one connection.',
      why: 'Hand-written JDBC is mostly resource handling that is easy to get wrong. The template removes that boilerplate but not the SQL: you still write the statements and must understand transactions and what a query costs.',
      who: [
        { name: 'JdbcTemplate / NamedParameterJdbcTemplate', role: 'execute SQL and run the callbacks' },
        { name: 'RowMapper and ResultSetExtractor', role: 'turn rows into Java values' },
        { name: 'SQLExceptionTranslator', role: 'maps MySQL error codes to DuplicateKeyException, BadSqlGrammarException and others' },
        { name: 'DataSourceTransactionManager and TransactionTemplate', role: 'bind one connection to the thread and commit or roll back' },
      ],
      where: 'Between the service layer and the DataSource, on the same pooled connections as everything else.',
      when: 'Each template call; a transaction manager makes the calls inside a TransactionTemplate callback reuse one connection.',
      how: [
        'The template gets a connection from the DataSource (or the one bound to the thread).',
        'It prepares the statement, binds the arguments (named ones are rewritten to ?) and executes it.',
        'The RowMapper or extractor reads the ResultSet.',
        'It closes ResultSet, statement and connection.',
        'A vendor error becomes a DataAccessException subclass with the SQLException as its cause.',
      ],
    },
  },
  {
    id: 'compare',
    title: 'JDBC vs JPA vs Hibernate',
    group: 'compare',
    explain: {
      what: 'Plain JDBC, Spring JDBC and JPA with Hibernate are three ways to reach the same database. JDBC gives full control and full responsibility. Spring JDBC keeps your SQL and removes resource handling. JPA maps entities to tables, and Hibernate (the JPA provider) generates the SQL, manages the persistence context and runs everything through JDBC.',
      why: 'Choosing between them is a trade-off, not a ranking: SQL control, mapping effort, boilerplate and abstraction differ. None is universally faster, and JPA does not remove SQL, connections or transactions; it hides who writes them.',
      who: [
        { name: 'Your service', role: 'chooses the access path for each operation' },
        { name: 'JdbcTemplate', role: 'path B: your SQL, the template\'s resource handling' },
        { name: 'EntityManager and Hibernate', role: 'path C: entities in, SQL generated, JDBC underneath' },
        { name: 'The JDBC API and the driver', role: 'shared by all three paths' },
      ],
      where: 'All three can live in the same Spring application. Sharing a transaction between JdbcTemplate and JPA works when both use the same DataSource under a JpaTransactionManager, and needs deliberate configuration.',
      when: 'At design time (which path for which operation) and at run time (every path ends as SQL on a pooled JDBC connection).',
      how: [
        'Path A: Connection, PreparedStatement, ResultSet written by hand.',
        'Path B: JdbcTemplate with a RowMapper.',
        'Path C: EntityManager.find or persist; Hibernate prepares the SQL.',
        'All three hand SQL to the driver on a java.sql.Connection.',
        'Inside one JPA transaction Hibernate and JdbcTemplate used the same MySQL session in the experiment.',
      ],
    },
  },
]

// ----------------------------------------------------------------------------------------------------------------- diagrams

const node = (id, label, sublabel, description) => ({ id, label, sublabel, description })
const edge = (source, target, secondary = false) => ({ id: `${source}->${target}`, source, target, ...(secondary ? { secondary: true } : {}) })

/** Per diagram: nodes, edges and layout. Ids must equal the server catalogue's `diagrams` (checked in tests). */
export const DIAGRAMS = {
  architecture: {
    nodes: [
      node('application', 'Your application', 'calls java.sql interfaces', 'Code that needs data: a service, a repository, JdbcTemplate or Hibernate.'),
      node('jdbcApi', 'JDBC API', 'java.sql / javax.sql', 'Interfaces such as Connection, PreparedStatement and DataSource. Same for every database.'),
      node('driver', 'JDBC driver', 'MySQL Connector/J', 'Implements the interfaces and speaks MySQL\'s protocol.'),
      node('database', 'MySQL', 'sessions and tables', 'Runs the SQL; each Connection is one session.'),
    ],
    edges: [edge('application', 'jdbcApi'), edge('jdbcApi', 'driver'), edge('driver', 'database')],
    layout: {
      wide: {
        positions: { application: cell(0, 0), jdbcApi: cell(1, 0), driver: cell(1, 1), database: cell(2, 1) },
        handles: { 'application->jdbcApi': ['r-s', 'l-t'], 'jdbcApi->driver': ['b-s', 't-t'], 'driver->database': ['r-s', 'l-t'] },
      },
      compact: { order: ['application', 'jdbcApi', 'driver', 'database'] },
      heights: { wide: 330 },
      nodeMeta: {
        application: { kind: 'business', stage: 1 }, jdbcApi: { kind: 'persistence', stage: 2 }, driver: { kind: 'persistence', stage: 3 }, database: { kind: 'database', stage: 4 },
      },
      lanes: [
        { id: 'app', label: 'Your code', kind: 'business', nodes: ['application'] },
        { id: 'api', label: 'JDBC API', caption: 'java.sql', kind: 'persistence', nodes: ['jdbcApi'] },
        { id: 'drv', label: 'Driver', caption: 'Connector/J', kind: 'persistence', nodes: ['driver'] },
        { id: 'db', label: 'Database', kind: 'database', nodes: ['database'] },
      ],
    },
  },
  statement: {
    nodes: [
      node('connection', 'Connection', 'a database session', 'Creates statements and owns the transaction state.'),
      node('statement', 'Statement / PreparedStatement', 'SQL text, parameters', 'Holds the SQL (with ? placeholders when prepared) and sends it.'),
      node('database', 'MySQL', 'executes the statement', 'Runs the SQL and returns rows or a row count.'),
      node('resultSet', 'ResultSet / update count', 'rows or a number', 'A cursor over the rows, or the number of rows affected.'),
    ],
    edges: [edge('connection', 'statement'), edge('statement', 'connection'), edge('statement', 'database'), edge('database', 'resultSet'), edge('statement', 'resultSet')],
    layout: {
      wide: {
        positions: { connection: cell(0, 0), statement: cell(1, 0), database: cell(2, 0), resultSet: cell(2, 1) },
        handles: {
          'connection->statement': ['r-s', 'l-t'],
          'statement->connection': ['tl-s', 'tr-t'],
          'statement->database': ['r-s', 'l-t'],
          'database->resultSet': ['b-s', 't-t'],
          'statement->resultSet': ['b-s', 'l-t'],
        },
      },
      compact: {
        order: ['connection', 'statement', 'database', 'resultSet'],
        handles: { 'statement->connection': ['l-s', 'l-t'], 'statement->resultSet': ['r-s', 'r-t'] },
      },
      heights: { wide: 380 },
      nodeMeta: {
        connection: { kind: 'persistence', stage: 1 }, statement: { kind: 'persistence', stage: 2 }, database: { kind: 'database', stage: 3 }, resultSet: { kind: 'business', stage: 4 },
      },
      lanes: [
        { id: 'jdbc', label: 'JDBC objects', kind: 'persistence', nodes: ['connection', 'statement'] },
        { id: 'db', label: 'Database', kind: 'database', nodes: ['database'] },
        { id: 'result', label: 'Result', kind: 'business', nodes: ['resultSet'] },
      ],
    },
  },
  transaction: {
    nodes: [
      node('connectionA', 'Connection A', 'does the work', 'The session that runs the statements of the transaction.'),
      node('transaction', 'Open transaction', 'pending changes', 'Changes exist only inside A\'s transaction until commit() or rollback().'),
      node('database', 'MySQL', 'committed data', 'What every other session can see.'),
      node('connectionB', 'Connection B', 'only reads', 'A separate session used to show what other sessions can see.'),
    ],
    edges: [edge('connectionA', 'transaction'), edge('transaction', 'database'), edge('transaction', 'connectionA'), edge('connectionB', 'database')],
    layout: {
      wide: {
        positions: { connectionA: cell(0, 0), transaction: cell(1, 0), database: cell(2, 0), connectionB: cell(2, 1) },
        handles: {
          'connectionA->transaction': ['r-s', 'l-t'],
          'transaction->database': ['r-s', 'l-t'],
          'transaction->connectionA': ['tl-s', 'tr-t'],
          'connectionB->database': ['t-s', 'b-t'],
        },
      },
      compact: {
        order: ['connectionA', 'transaction', 'database', 'connectionB'],
        handles: { 'transaction->connectionA': ['l-s', 'l-t'], 'connectionB->database': ['r-s', 'r-t'] },
      },
      heights: { wide: 380 },
      nodeMeta: {
        connectionA: { kind: 'business', stage: 'A' }, transaction: { kind: 'persistence', stage: 1 }, database: { kind: 'database', stage: 2 }, connectionB: { kind: 'client', stage: 'B' },
      },
      lanes: [
        { id: 'a', label: 'Session A', kind: 'business', nodes: ['connectionA', 'transaction'] },
        { id: 'db', label: 'Database', kind: 'database', nodes: ['database'] },
        { id: 'b', label: 'Session B', kind: 'client', nodes: ['connectionB'] },
      ],
    },
  },
  pool: {
    nodes: [
      node('application', 'Application threads', 'borrow and return', 'Request threads that need a connection for a short time.'),
      node('pool', 'HikariCP pool', 'bounded, reuses', 'Lends connections, tracks active, idle, total and waiting.'),
      node('connection', 'Pooled connection', 'proxy over a session', 'close() returns it to the pool; the session stays open.'),
      node('database', 'MySQL', 'physical sessions', 'Sees the sessions, not the borrow and return calls.'),
    ],
    edges: [edge('application', 'pool'), edge('pool', 'application'), edge('pool', 'connection'), edge('connection', 'pool'), edge('connection', 'database')],
    layout: {
      wide: {
        positions: { application: cell(0, 0), pool: cell(1, 0), connection: cell(1, 1), database: cell(2, 1) },
        handles: {
          'application->pool': ['r-s', 'l-t'],
          'pool->application': ['tl-s', 'tr-t'],
          'pool->connection': ['bl-s', 'tl-t'],
          'connection->pool': ['tr-s', 'br-t'],
          'connection->database': ['r-s', 'l-t'],
        },
      },
      compact: {
        order: ['application', 'pool', 'connection', 'database'],
        handles: { 'pool->application': ['l-s', 'l-t'], 'connection->pool': ['r-s', 'r-t'] },
      },
      heights: { wide: 380 },
      nodeMeta: {
        application: { kind: 'business', stage: 1 }, pool: { kind: 'persistence', stage: 2 }, connection: { kind: 'persistence', stage: 3 }, database: { kind: 'database', stage: 4 },
      },
      lanes: [
        { id: 'app', label: 'Application', kind: 'business', nodes: ['application'] },
        { id: 'pool', label: 'Connection pool', kind: 'persistence', nodes: ['pool', 'connection'] },
        { id: 'db', label: 'Database', kind: 'database', nodes: ['database'] },
      ],
    },
  },
  spring: {
    nodes: [
      node('service', 'Your service', 'calls the template', 'Business code that needs rows.'),
      node('jdbcTemplate', 'JdbcTemplate', 'resources, mapping, exceptions', 'Runs the statement and the callbacks, then cleans up.'),
      node('dataSource', 'DataSource', 'HikariCP pool', 'Where the template gets its connection.'),
      node('driver', 'JDBC driver', 'MySQL Connector/J', 'Turns JDBC calls into the MySQL protocol.'),
      node('database', 'MySQL', 'executes', 'Runs the SQL.'),
    ],
    edges: [edge('service', 'jdbcTemplate'), edge('jdbcTemplate', 'service'), edge('jdbcTemplate', 'dataSource'), edge('dataSource', 'driver'), edge('driver', 'database')],
    layout: {
      wide: {
        positions: { service: cell(0, 0), jdbcTemplate: cell(1, 0), dataSource: cell(1, 1), driver: cell(2, 1), database: cell(3, 1) },
        handles: {
          'service->jdbcTemplate': ['r-s', 'l-t'],
          'jdbcTemplate->service': ['tl-s', 'tr-t'],
          'jdbcTemplate->dataSource': ['b-s', 't-t'],
          'dataSource->driver': ['r-s', 'l-t'],
          'driver->database': ['r-s', 'l-t'],
        },
      },
      compact: { order: ['service', 'jdbcTemplate', 'dataSource', 'driver', 'database'], handles: { 'jdbcTemplate->service': ['l-s', 'l-t'] } },
      heights: { wide: 380 },
      fullWidth: true,
      minWidth: 900,
      nodeMeta: {
        service: { kind: 'business', stage: 1 }, jdbcTemplate: { kind: 'persistence', stage: 2 }, dataSource: { kind: 'persistence', stage: 3 }, driver: { kind: 'persistence', stage: 4 }, database: { kind: 'database', stage: 5 },
      },
      lanes: [
        { id: 'svc', label: 'Your code', kind: 'business', nodes: ['service'] },
        { id: 'spring', label: 'Spring JDBC', kind: 'persistence', nodes: ['jdbcTemplate', 'dataSource'] },
        { id: 'jdbc', label: 'JDBC driver', kind: 'persistence', nodes: ['driver'] },
        { id: 'db', label: 'Database', kind: 'database', nodes: ['database'] },
      ],
    },
  },
  compare: {
    nodes: [
      node('service', 'Your service', 'chooses a path', 'Business code that needs the same data.'),
      node('jdbc', 'Path A: JDBC', 'Connection, PreparedStatement', 'You write the SQL, binding, mapping and closing.'),
      node('jdbcTemplate', 'Path B: JdbcTemplate', 'your SQL, its plumbing', 'You write the SQL; the template handles resources and exceptions.'),
      node('entityManager', 'Path C: EntityManager', 'find, persist, JPQL', 'You work with entities.'),
      node('hibernate', 'Hibernate', 'SQL generation, persistence context', 'Generates the SQL from the mapping and tracks changes.'),
      node('jdbcApi', 'JDBC API', 'java.sql', 'All three paths use it.'),
      node('driver', 'JDBC driver', 'MySQL Connector/J', 'Sends the SQL to MySQL.'),
      node('database', 'MySQL', 'executes', 'Runs the SQL.'),
    ],
    edges: [
      edge('service', 'jdbc'), edge('service', 'jdbcTemplate'), edge('service', 'entityManager'), edge('entityManager', 'hibernate'),
      edge('jdbc', 'jdbcApi'), edge('jdbcTemplate', 'jdbcApi'), edge('hibernate', 'jdbcApi'), edge('jdbcApi', 'driver'), edge('driver', 'database'),
    ],
    layout: {
      wide: {
        positions: {
          service: cell(0, 1), jdbc: cell(1, 0), jdbcTemplate: cell(1, 1), entityManager: cell(1, 2), hibernate: cell(2, 2),
          jdbcApi: cell(3, 1), driver: cell(4, 1), database: cell(5, 1),
        },
        handles: {
          'service->jdbc': ['r-s', 'l-t'], 'service->jdbcTemplate': ['r-s', 'l-t'], 'service->entityManager': ['r-s', 'l-t'],
          'entityManager->hibernate': ['r-s', 'l-t'], 'jdbc->jdbcApi': ['r-s', 'l-t'], 'jdbcTemplate->jdbcApi': ['r-s', 'l-t'],
          'hibernate->jdbcApi': ['r-s', 'b-t'], 'jdbcApi->driver': ['r-s', 'l-t'], 'driver->database': ['r-s', 'l-t'],
        },
      },
      compact: {
        order: ['service', 'jdbc', 'jdbcTemplate', 'entityManager', 'hibernate', 'jdbcApi', 'driver', 'database'],
        handles: {
          'service->jdbcTemplate': ['r-s', 'r-t'], 'service->entityManager': ['r-s', 'r-t'], 'jdbc->jdbcApi': ['r-s', 'r-t'], 'jdbcTemplate->jdbcApi': ['r-s', 'r-t'],
        },
      },
      heights: { wide: 540 },
      fullWidth: true,
      minWidth: 900,
      nodeMeta: {
        service: { kind: 'business', stage: 1 }, jdbc: { kind: 'persistence', stage: 'A' }, jdbcTemplate: { kind: 'persistence', stage: 'B' },
        entityManager: { kind: 'persistence', stage: 'C' }, hibernate: { kind: 'persistence' }, jdbcApi: { kind: 'persistence', stage: 2 },
        driver: { kind: 'persistence', stage: 3 }, database: { kind: 'database', stage: 4 },
      },
      edgeMeta: {
        'service->jdbcTemplate': { compactOffset: 30 }, 'service->entityManager': { compactOffset: 55 }, 'jdbc->jdbcApi': { compactOffset: 80 }, 'jdbcTemplate->jdbcApi': { compactOffset: 105 },
      },
      lanes: [
        { id: 'svc', label: 'Your code', kind: 'business', nodes: ['service'] },
        { id: 'a', label: 'Path A', caption: 'plain JDBC', kind: 'persistence', nodes: ['jdbc'] },
        { id: 'b', label: 'Path B', caption: 'Spring JDBC', kind: 'persistence', nodes: ['jdbcTemplate'] },
        { id: 'c', label: 'Path C', caption: 'JPA / Hibernate', kind: 'persistence', nodes: ['entityManager', 'hibernate'] },
        { id: 'shared', label: 'Shared', caption: 'JDBC to MySQL', kind: 'database', nodes: ['jdbcApi', 'driver', 'database'] },
      ],
    },
  },
}

const sim = (id, title, nodes, edgeFrom, edgeTo, explanation) => ({
  id, title, nodes, edgeFrom, edgeTo, direction: 'REQUEST', provenance: 'CONCEPTUAL', explanation, evidence: [], sql: [], codeRefId: null,
})

/** A conceptual (SIMULATED) walkthrough per topic. Nothing here was observed. */
const WALKTHROUGHS = {
  fundamentals: ['architecture', [
    sim('layers', 'Three layers between your code and MySQL', ['application', 'jdbcApi'], 'application', 'jdbcApi',
      'Your code uses only interfaces from java.sql and javax.sql. It does not know which database or driver is behind them.'),
    sim('driver', 'The driver implements the API', ['jdbcApi', 'driver'], 'jdbcApi', 'driver',
      'The driver is a library that implements those interfaces for one database product and is found through the Java ServiceLoader.'),
    sim('wire', 'The driver speaks the database\'s protocol', ['driver', 'database'], 'driver', 'database',
      'For MySQL the driver opens a socket, authenticates, and sends commands in MySQL\'s own protocol. Each Connection corresponds to one server-side session.'),
  ]],
  statements: ['statement', [
    sim('prepare', 'A connection creates a statement', ['connection', 'statement'], 'connection', 'statement',
      'prepareStatement(sql) returns a statement bound to this connection. The SQL text holds ? placeholders, never the values.'),
    sim('execute', 'Execution sends the SQL and the bound values', ['statement', 'database'], 'statement', 'database',
      'executeQuery, executeUpdate or execute passes the statement and the values to the driver, which sends them to MySQL as data.'),
    sim('rows', 'Rows come back as a cursor', ['database', 'resultSet'], 'database', 'resultSet',
      'A query returns a ResultSet positioned before the first row; an update returns a count. next() moves through the rows.'),
    sim('close', 'Everything that was opened is closed', ['statement', 'connection'], 'statement', 'connection',
      'ResultSet, statement and connection are closed in reverse order. try-with-resources does it even when an exception is thrown.'),
  ]],
  crud: ['statement', [
    sim('insert', 'INSERT and the generated key', ['connection', 'statement', 'database'], 'statement', 'database',
      'executeUpdate returns the rows inserted; with RETURN_GENERATED_KEYS the AUTO_INCREMENT id is read from getGeneratedKeys().'),
    sim('select', 'SELECT maps rows by hand', ['database', 'resultSet'], 'database', 'resultSet',
      'In plain JDBC the loop, the column names and the Java types are yours to write.'),
    sim('counts', 'UPDATE and DELETE return counts', ['statement', 'database'], 'statement', 'database',
      'A statement that matches no row returns 0. It is not an error: the count is how code notices.'),
    sim('batch', 'A batch sends several parameter sets', ['statement', 'database'], 'statement', 'database',
      'addBatch stores parameter sets and executeBatch sends them. Whether MySQL receives one statement or many depends on driver settings.'),
  ]],
  transactions: ['transaction', [
    sim('begin', 'setAutoCommit(false) opens a transaction scope', ['connectionA', 'transaction'], 'connectionA', 'transaction',
      'Statements on A are now grouped. Their changes are visible to A itself but not to other sessions.'),
    sim('other', 'Another session cannot see pending changes', ['connectionB', 'database'], 'connectionB', 'database',
      'B reads committed data only (at REPEATABLE READ and READ COMMITTED), so it sees the old values.'),
    sim('commit', 'commit() makes the changes permanent', ['transaction', 'database'], 'transaction', 'database',
      'All statements of the transaction become permanent and visible together.'),
    sim('rollback', 'rollback() discards them', ['transaction', 'connectionA'], 'transaction', 'connectionA',
      'All statements are discarded together, including ones whose executeUpdate returned normally.'),
  ]],
  pooling: ['pool', [
    sim('borrow', 'A thread borrows a connection', ['application', 'pool'], 'application', 'pool',
      'getConnection() lends an idle connection, or opens a new one while the pool is below its maximum.'),
    sim('use', 'The connection talks to MySQL', ['connection', 'database'], 'connection', 'database',
      'While borrowed, the connection belongs to one thread. MySQL sees an ordinary session.'),
    sim('return', 'close() returns it', ['connection', 'pool'], 'connection', 'pool',
      'close() on the proxy marks the connection idle again. The physical session is not closed.'),
    sim('wait', 'When all are borrowed, callers wait', ['pool', 'application'], 'pool', 'application',
      'A further caller waits up to connectionTimeout and then receives an exception.'),
  ]],
  spring: ['spring', [
    sim('call', 'The service calls the template', ['service', 'jdbcTemplate'], 'service', 'jdbcTemplate',
      'You give JdbcTemplate the SQL, the arguments and, for queries, a RowMapper.'),
    sim('connection', 'The template gets a connection', ['jdbcTemplate', 'dataSource'], 'jdbcTemplate', 'dataSource',
      'From the DataSource, or the connection already bound to the thread by a transaction manager.'),
    sim('execute', 'Statement, execution and mapping', ['dataSource', 'driver'], 'dataSource', 'driver',
      'It prepares and executes the statement through the driver and calls your mapper for each row.'),
    sim('return', 'Cleanup and translated exceptions', ['jdbcTemplate', 'service'], 'jdbcTemplate', 'service',
      'It closes every resource and, on failure, throws a DataAccessException subclass.'),
  ]],
  compare: ['compare', [
    sim('a', 'Path A: you do everything', ['service', 'jdbc'], 'service', 'jdbc',
      'Connection, PreparedStatement and ResultSet are yours to open, use and close.'),
    sim('b', 'Path B: the template does the plumbing', ['service', 'jdbcTemplate'], 'service', 'jdbcTemplate',
      'Your SQL stays; resource handling and exception translation move into the template.'),
    sim('c', 'Path C: Hibernate writes the SQL', ['service', 'entityManager'], 'service', 'entityManager',
      'You work with entities; Hibernate generates the SQL and tracks changes in the persistence context.'),
    sim('shared', 'All three end at the same place', ['jdbcApi', 'driver'], 'jdbcApi', 'driver',
      'Every path hands SQL to the JDBC API on a pooled connection, and the driver sends it to MySQL.'),
  ]],
}

export function diagramFor(id) {
  const d = DIAGRAMS[id]
  return { nodes: d.nodes, edges: d.edges, layout: d.layout }
}

/** The SIMULATED walkthrough for a topic. */
export function simulatedScenario(topicId) {
  const [diagram, steps] = WALKTHROUGHS[topicId]
  return { id: `jdbc-sim-${topicId}`, title: `${TOPICS.find((t) => t.id === topicId).title} (conceptual walkthrough)`, mode: 'SIMULATED', ...diagramFor(diagram), steps }
}

/** The LIVE scenario for one run: the server's steps on the experiment's diagram. */
export function liveScenario(run, info) {
  return { id: `jdbc-live-${run.experimentId}`, title: info.title, mode: 'LIVE', ...diagramFor(info.diagram), steps: run.steps }
}

export const SIMULATED_NOTES = [
  {
    id: 'isolation-levels',
    title: 'SERIALIZABLE, phantom reads and locking',
    body: 'The isolation experiment runs REPEATABLE READ, READ COMMITTED and READ UNCOMMITTED. SERIALIZABLE, phantom reads and the way InnoDB locks rows and gaps are not run here: they need concurrent writers that wait for each other, and the lab deliberately avoids timing-dependent behaviour. They are explained, not observed.',
  },
  {
    id: 'driver-internals',
    title: 'What the driver does on the wire',
    body: 'MySQL Connector/J opens a socket, performs the handshake and authentication, and sends commands in MySQL\'s protocol. The lab observes the JDBC calls the application makes and, with MySQL\'s own session counters, how many statements the server received. The bytes on the wire are not captured.',
  },
  {
    id: 'pool-internals',
    title: 'How HikariCP finds a free connection',
    body: 'HikariCP keeps its connections in a concurrent structure and hands a free one to the caller, creating a new one asynchronously while below maximumPoolSize. Its housekeeping (idle timeout, max lifetime, keepalive) closes and replaces connections over time. The lab shows the pool\'s own metrics and the borrow and return behaviour, not these internals.',
  },
  {
    id: 'server-prepared',
    title: 'Server-side prepared statements',
    body: 'With useServerPrepStmts=true the driver would ask MySQL to parse the statement once and then send only parameters. SpringLab uses Connector/J\'s default (client-side preparation), so that mode is not run. Both modes keep values as data.',
  },
  {
    id: 'unsafe-sql',
    title: 'The unsafe SQL construction',
    body: 'The injection experiment shows the text an unsafe concatenation WOULD produce but never executes it. No unsafe query is ever run by the lab, and no SQL text from the browser is ever used.',
  },
]

export const STATIC_ASSUMPTIONS = [
  'The lab runs real JDBC, Spring JDBC and Hibernate against the local MySQL database that SpringLab uses; nothing is mocked or replayed.',
  'Every experiment uses temporary rows in the jdbc_lab_* tables that it creates and removes itself (the number removed is reported). No SpringLab application table is read or changed.',
  'SQL shown is the text the application handed to the JDBC driver, with ? placeholders. It is not proof that MySQL executed it: where that matters the experiment also reads MySQL\'s own per-session counters (Com_insert, Com_commit and so on).',
  'A successful executeUpdate does not mean the change is committed. The transaction experiments say, for each step, whether the transaction is open, committed or rolled back and what a second connection can see.',
  'Pool experiments use a dedicated bounded HikariCP pool created for the run and closed afterwards. The application\'s own pool is only read (counts) and never changed.',
  'MySQL Connector/J is used with its defaults: statements are prepared on the client and batches are not rewritten. Other drivers or settings behave differently.',
  'Statement counts and timings are single measurements on tiny tables. The lab makes no performance claim about JDBC, Spring JDBC or JPA.',
]

/** Key points for the Explain stage, each linked to the experiments that show it. */
export const TAKEAWAYS = [
  { text: 'A pooled DataSource keeps the physical session open after close(); DriverManager ends it. That is why applications use a pool.', experiments: ['jdbc-drivermanager-vs-datasource', 'pool-acquire-release'] },
  { text: 'Bound parameters are data. The same quote-and-OR text that would rewrite a concatenated query matches no row when it is bound.', experiments: ['jdbc-prepared-select', 'jdbc-sql-injection'] },
  { text: 'executeUpdate returns a row count, and 0 is a normal answer. MySQL\'s own counters show what it really received.', experiments: ['jdbc-crud-single', 'jdbc-batch-insert'] },
  { text: 'A statement that returned normally is not committed. Until commit() another connection cannot see it, and rollback() discards all of it.', experiments: ['jdbc-commit', 'jdbc-rollback', 'jdbc-autocommit'] },
  { text: 'A failing statement undoes only itself. The earlier statements of the transaction stay pending until the code rolls back; a savepoint undoes only later work.', experiments: ['jdbc-rollback-on-failure', 'jdbc-savepoint'] },
  { text: 'The isolation level decides what a second read sees: a stable snapshot, the other session\'s committed change, or even its uncommitted change.', experiments: ['jdbc-isolation'] },
  { text: 'A pool is bounded on purpose. Leaked or long-held connections turn into waiting callers and a timeout.', experiments: ['pool-exhaustion', 'pool-multiple'] },
  { text: 'JdbcTemplate keeps your SQL and removes the resource handling and exception boilerplate; it does not remove transactions or the need to understand SQL.', experiments: ['spring-jdbc-query', 'spring-jdbc-exception-translation', 'spring-jdbc-transaction'] },
  { text: 'JPA and Hibernate sit on top of JDBC: same pooled connection, same driver, same MySQL session. They change who writes the SQL, not whether SQL exists.', experiments: ['compare-read', 'compare-insert', 'compare-same-session'] },
]

/** The comparison table of the topic "JDBC vs JPA vs Hibernate" (explanation, not an observation). */
export const COMPARISON = {
  columns: ['Concern', 'JDBC', 'Spring JDBC', 'JPA / Hibernate'],
  rows: [
    ['SQL control', 'Direct', 'Direct', 'Often abstracted'],
    ['Mapping', 'Manual', 'RowMapper and similar', 'Entity mapping'],
    ['Boilerplate', 'Higher', 'Lower', 'Lower for CRUD'],
    ['Relationships', 'Manual joins', 'Manual joins', 'ORM mappings'],
    ['Abstraction level', 'Low', 'Medium', 'High'],
    ['Query control', 'High', 'High', 'Depends on the query approach'],
  ],
}

/** The entity state or transaction state shown for a step is parsed from the server's evidence: labels used by the experiments. */
export const EVIDENCE_LABELS = Object.freeze({
  transactionState: 'Transaction state of connection A',
  pool: 'Lab pool',
})

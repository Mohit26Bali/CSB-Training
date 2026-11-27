/**
 * JPA Relationship Lab: the module's authored content. Everything here is explanation or layout. Observations (SQL, rows,
 * object state, counts) never appear in this file: they come from LIVE runs on the server (POST /api/lab/jpa/...).
 * The entity/table diagram is not authored either: it is built from the server's mapping description.
 */
import { cell } from '../schema/layout'

export const MODULE = Object.freeze({
  id: 'jpa-relationships',
  title: 'JPA Relationship Lab',
  topicSlug: 'jpa-hibernate', // Challenge Arena topic used by "Test yourself"
  summary:
    'See how Java entity relationships become tables and foreign keys, which side really writes them, and what SQL Hibernate prepares when you cascade, fetch, flush and commit.',
})

/** The relationship families the Visualize stage can show, with the entities each one focuses on. */
export const FAMILIES = [
  { id: 'one-to-one', label: 'One-to-one', detail: 'Account and Profile', focus: ['Account', 'Profile'] },
  { id: 'one-to-many', label: 'One-to-many / many-to-one', detail: 'Department and Employee', focus: ['Department', 'Employee'] },
  { id: 'many-to-many', label: 'Many-to-many', detail: 'Student and Course (join table)', focus: ['Student', 'Course'] },
  { id: 'cascade-all', label: 'Cascade ALL', detail: 'Project and Task', focus: ['Project', 'Task'] },
  { id: 'orphan', label: 'Cascade ALL + orphanRemoval', detail: 'Team and Member', focus: ['Team', 'Member'] },
]

/** One 5W1H per major topic. Each is checked by validateExplain5W1H in the tests and again when rendered. */
export const TOPICS = [
  {
    id: 'orm-jpa',
    title: 'ORM, JPA and Hibernate',
    explain: {
      what: 'Object-relational mapping (ORM) connects Java objects to relational tables. Jakarta Persistence (JPA) is the specification that defines the annotations and the EntityManager API; Hibernate is the persistence provider that implements it. An entity is a class annotated with @Entity that is mapped to a table, with an @Id that maps to the primary key.',
      why: 'Objects reference each other; tables relate through keys. Without a mapper every query, join and insert is hand-written SQL plus code to copy rows into objects and back. JPA lets you work with entities and associations and generates the SQL, but the database stays relational, so you still have to understand the SQL it produces.',
      who: [
        { name: 'Entity classes (@Entity, @Id)', role: 'describe the tables and columns; the lab uses Department, Employee, Account, Profile, Student, Course, Project, Task, Team and Member' },
        { name: 'EntityManager', role: 'the JPA API used to find, persist, merge and remove entities inside a persistence context' },
        { name: 'Hibernate', role: 'the JPA provider: tracks changes, generates SQL for the MySQL dialect and sends it through JDBC' },
        { name: 'Spring Data JPA repositories', role: 'generated interfaces that call the EntityManager so services do not write boilerplate' },
        { name: 'MySQL', role: 'stores the rows and enforces primary keys, unique keys and foreign key constraints' },
      ],
      where: 'In the persistence layer of SpringLab: services call repositories, repositories use the EntityManager, Hibernate talks to MySQL through JDBC. Flyway creates the schema; Hibernate only validates it (ddl-auto = validate).',
      when: 'At runtime, whenever a service touches a repository. The mapping metadata is read once at startup; each transaction then gets its own persistence context.',
      how: [
        'At startup Hibernate reads the entity annotations and validates them against the schema Flyway created.',
        'A service method opens a transaction and the EntityManager creates a persistence context for it.',
        'Finding or persisting entities makes them managed: Hibernate remembers what it loaded.',
        'At flush Hibernate compares managed entities with what it loaded and generates INSERT, UPDATE or DELETE statements.',
        'The statements go to MySQL through JDBC and the commit makes them permanent.',
      ],
    },
  },
  {
    id: 'one-to-one',
    title: 'One-to-one: Account and Profile',
    explain: {
      what: '@OneToOne links one row to at most one row of another table. The side with @JoinColumn owns the association and holds the foreign key; here Profile.account maps account_id with unique = true. The other side, Account.profile, uses mappedBy and is the inverse side.',
      why: 'Some data belongs to exactly one parent but is kept apart (optional details, large columns, a different lifecycle). The unique foreign key is what turns an ordinary many-to-one into a one-to-one at the database level.',
      who: [
        { name: 'Profile (owning side)', role: 'declares @OneToOne @JoinColumn and its table jpa_lab_profile contains account_id' },
        { name: 'Account (inverse side)', role: 'declares @OneToOne(mappedBy = "account"); jpa_lab_account has no column for the profile' },
        { name: 'UNIQUE constraint', role: 'created by @JoinColumn(unique = true) and enforced by MySQL, not by Hibernate' },
      ],
      where: 'Across the object model (two references, one on each class) and the schema (one foreign key column in one table).',
      when: 'When the pair is persisted: the account row must exist before the profile row that references it. A unidirectional mapping (only Profile.account) gives the same schema; mappedBy only adds the second Java reference.',
      how: [
        'Create both objects and link both references with a helper (attachProfile).',
        'persist(account) inserts the account row; with IDENTITY MySQL returns the generated id.',
        'persist(profile) inserts the profile row with account_id set from Profile.account.',
        'A second profile for the same account violates the UNIQUE constraint and MySQL refuses the INSERT.',
      ],
    },
  },
  {
    id: 'owning-side',
    title: 'Owning side and mappedBy: Department and Employee',
    explain: {
      what: 'In a bidirectional @OneToMany / @ManyToOne association the owning side is the one that maps the foreign key (Employee.department with @JoinColumn). The other side declares mappedBy = "department": it is the inverse side, kept for navigation and queries. Unidirectional means only one Java reference exists; bidirectional means both do.',
      why: 'A foreign key is a single column, but the object model has two references. JPA needs one of them to be authoritative. Only the owning side is used when Hibernate writes the foreign key, so changing the inverse collection alone does not change the database.',
      who: [
        { name: 'Employee.department (owning)', role: '@ManyToOne @JoinColumn(name = "department_id"): decides the value stored in the foreign key' },
        { name: 'Department.employees (inverse)', role: '@OneToMany(mappedBy = "department"): a list that is read, never written, as a foreign key' },
        { name: 'Helper methods (addEmployee)', role: 'update both Java references so the object graph stays consistent' },
        { name: 'Hibernate flush', role: 'builds the INSERT or UPDATE from the owning side only' },
      ],
      where: 'In the entity classes (annotations and helper methods) and in the employee table (department_id). The department table has no column for the relationship.',
      when: 'At flush time, when Hibernate generates the statements; with IDENTITY, at persist() for a new employee.',
      how: [
        'Set only Employee.department: the foreign key is written, the department list in memory is out of date.',
        'Add the employee only to Department.employees: the foreign key stays NULL.',
        'Call department.addEmployee(employee): both references agree and the foreign key is written.',
        'Reloading the department later returns the list that matches the database.',
      ],
    },
  },
  {
    id: 'many-to-many',
    title: 'Many-to-many: Student and Course',
    explain: {
      what: '@ManyToMany relates many rows to many rows through a third table, the join table. The owning side declares @JoinTable with joinColumns (the column referencing this entity) and inverseJoinColumns (the column referencing the other entity). The other side uses mappedBy.',
      why: 'A foreign key cannot hold a list, so a many-to-many needs rows that pair the two ids. Real applications often replace it with an explicit association entity (for example Enrollment) as soon as the link needs its own data such as a date or a grade.',
      who: [
        { name: 'Student.courses (owning)', role: '@ManyToMany @JoinTable(name = "jpa_lab_enrollment", joinColumns = student_id, inverseJoinColumns = course_id)' },
        { name: 'Course.students (inverse)', role: '@ManyToMany(mappedBy = "courses"); changing it alone writes nothing' },
        { name: 'jpa_lab_enrollment', role: 'the join table; its primary key is the pair of both foreign keys' },
      ],
      where: 'Only the join table stores the relationship; neither entity table gets a foreign key column.',
      when: 'At flush, when Hibernate compares the owning collection with what it loaded and inserts or deletes join rows.',
      how: [
        'Persist the student and the courses; there is no cascade, so each is persisted explicitly.',
        'Add courses to Student.courses; nothing is sent until flush.',
        'The flush inserts one join table row per course.',
        'Removing a course from Student.courses deletes only the join row; cascade REMOVE would be a mistake because the courses are shared.',
      ],
    },
  },
  {
    id: 'cascade',
    title: 'Cascade and orphanRemoval',
    explain: {
      what: 'Cascade propagates an EntityManager operation from a parent to the entities in an association: PERSIST, MERGE, REMOVE, REFRESH, DETACH, or ALL for all five. orphanRemoval = true is a separate feature: a child that is removed from the parent\'s collection is deleted.',
      why: 'Without cascade every child has to be persisted and removed by hand. With it, a parent that owns its children behaves as one unit. The two features answer different questions: cascade REMOVE reacts to deleting the PARENT, orphanRemoval reacts to removing a CHILD from the collection.',
      who: [
        { name: 'Department / Employee', role: 'no cascade: the employee is a separate entity that persist(department) does not reach' },
        { name: 'Project / Task', role: 'cascade = ALL without orphanRemoval' },
        { name: 'Team / Member', role: 'cascade = ALL with orphanRemoval = true' },
        { name: 'MySQL foreign keys', role: 'enforce that a parent cannot be deleted while children still reference it' },
      ],
      where: 'On the @OneToMany or @OneToOne association of the parent entity; never a substitute for the database constraints.',
      when: 'When an EntityManager operation runs on the parent (persist, merge, remove, refresh, detach) and, for orphanRemoval, at flush after a child was removed from the collection.',
      how: [
        'persist(parent) with cascade PERSIST also persists the children; without it only the parent is stored.',
        'Updating a managed parent produces one UPDATE at flush; no save() and no child statement.',
        'Removing a child from a cascade-ALL collection without orphanRemoval writes nothing and keeps the row.',
        'With orphanRemoval the same removal schedules a DELETE for the child.',
        'remove(parent) without cascade is refused by the foreign key; with cascade ALL the children are deleted first.',
      ],
    },
  },
  {
    id: 'fetching',
    title: 'Lazy and eager fetching',
    explain: {
      what: 'FetchType.LAZY loads an association when it is first used; FetchType.EAGER loads it together with the owner. The specification defaults are EAGER for @ManyToOne and @OneToOne and LAZY for @OneToMany and @ManyToMany. A lazy association is represented by a proxy or a collection wrapper.',
      why: 'Loading everything reachable from an entity is wasteful, loading nothing forces extra queries later. The fetch type is a default; the query decides what is really needed.',
      who: [
        { name: 'Hibernate proxies and PersistentBag', role: 'stand-ins that run the SELECT the first time they are used' },
        { name: 'Persistence context (session)', role: 'must be open when the lazy association is touched' },
        { name: 'LazyInitializationException', role: 'thrown when a lazy association is used after the session closed' },
        { name: 'spring.jpa.open-in-view', role: 'when true keeps the persistence context open for the whole web request; SpringLab sets it to false' },
      ],
      where: 'In the entity mapping (fetch attribute), in repository queries (fetch joins and entity graphs) and in the service layer where the transaction lives.',
      when: 'At the moment the association is first used, which may be long after the entity was loaded.',
      how: [
        'Loading a department does not load its employees; the collection is an uninitialised wrapper.',
        'Touching the collection inside the transaction runs a second SELECT.',
        'Touching it after the transaction ended throws LazyInitializationException.',
        'An EAGER @ManyToOne is loaded with the task, but a JPQL query ignores the fetch type and then loads each missing project with an extra SELECT.',
      ],
    },
  },
  {
    id: 'n-plus-one',
    title: 'The N+1 problem, JOIN FETCH and EntityGraph',
    explain: {
      what: 'N+1 is one query for N parents followed by one query per parent for a lazy association: N+1 statements. A JPQL join fetch or an EntityGraph tells Hibernate to load the association in the same statement.',
      why: 'The code looks harmless but the number of database round trips grows with the data, which is slow under load. Making everything EAGER does not fix it. Fetching several collections at once can multiply the rows returned, so each fix has a price.',
      who: [
        { name: 'DepartmentRepository', role: 'offers a plain query, a JPQL join fetch and an @EntityGraph query for the same departments' },
        { name: 'Hibernate', role: 'prepares the statements whose number the lab counts' },
        { name: 'Department.employees and Department.rooms', role: 'two collections used to show row multiplication' },
      ],
      where: 'In repository queries and the service code that uses the results.',
      when: 'When a lazy association is used inside a loop, or when a query is written without saying what it needs.',
      how: [
        'Load N departments with a plain query: one statement.',
        'Use each department\'s employees: one more statement per department.',
        'Load the same departments with join fetch or an entity graph: one statement, nothing extra when the employees are used.',
        'Fetching two collections in one statement returns employees times rooms rows per department.',
      ],
    },
  },
  {
    id: 'persistence-context',
    title: 'Persistence context, dirty checking, flush and commit',
    explain: {
      what: 'The persistence context is the set of entities an EntityManager currently manages, and it acts as a first-level cache. An entity is transient (unknown), managed (tracked), detached (known id, no longer tracked) or removed (scheduled for deletion). Dirty checking compares managed entities with the state Hibernate loaded; flush sends the resulting SQL; commit makes the transaction permanent.',
      why: 'Because Hibernate tracks changes, you do not call save() for every modification, and repeated finds of the same row cost nothing. The price is that SQL is not issued where the Java code changes a field, so you must know when flush happens.',
      who: [
        { name: 'EntityManager', role: 'persist, find, merge, remove, detach, flush and clear' },
        { name: 'Persistence context', role: 'holds managed entities and the snapshot used for dirty checking' },
        { name: 'Transaction manager', role: 'begins and commits the transaction that bounds the persistence context' },
        { name: 'JDBC and MySQL', role: 'receive the SQL at flush and make it durable at commit' },
      ],
      where: 'Inside a transaction in the service layer; in SpringLab one persistence context per transaction.',
      when: 'persist() may run SQL immediately (IDENTITY) or later; changes to managed entities are sent at flush, which happens before queries when needed and before commit.',
      how: [
        'persist() makes a new entity managed; with a MySQL IDENTITY key the INSERT runs at once to get the id.',
        'Changing a field of a managed entity prepares no SQL by itself.',
        'flush() runs dirty checking and sends an UPDATE; other transactions still see the old value.',
        'commit makes the change permanent and visible to other transactions.',
        'find() twice returns the same instance and prepares one SELECT; clear() empties the cache.',
      ],
    },
  },
]

// ----------------------------------------------------------------------------------------------- flow diagram

export const FLOW_NODES = [
  { id: 'application', label: 'Your service', sublabel: 'calls the EntityManager', description: 'Application code inside a @Transactional service method.' },
  { id: 'entityManager', label: 'EntityManager', sublabel: 'JPA API (Hibernate Session)', description: 'find, persist, merge, remove, flush, clear.' },
  { id: 'persistenceContext', label: 'Persistence context', sublabel: 'first-level cache', description: 'The entities this EntityManager manages, plus the snapshot used for dirty checking.' },
  { id: 'jdbc', label: 'JDBC', sublabel: 'prepares and sends SQL', description: 'Hibernate hands each SQL statement to JDBC; the lab records the text at this point.' },
  { id: 'database', label: 'MySQL', sublabel: 'executes, commits', description: 'Executes the statements and makes them permanent at commit.' },
]

export const FLOW_EDGES = [
  { id: 'application->entityManager', source: 'application', target: 'entityManager' },
  { id: 'entityManager->persistenceContext', source: 'entityManager', target: 'persistenceContext' },
  { id: 'entityManager->jdbc', source: 'entityManager', target: 'jdbc' },
  { id: 'jdbc->database', source: 'jdbc', target: 'database' },
]

export const FLOW_LAYOUT = {
  wide: {
    positions: {
      application: cell(0, 0),
      entityManager: cell(1, 0),
      persistenceContext: cell(1, 1),
      jdbc: cell(2, 0),
      database: cell(3, 0),
    },
    handles: {
      'application->entityManager': ['r-s', 'l-t'],
      'entityManager->persistenceContext': ['b-s', 't-t'],
      'entityManager->jdbc': ['r-s', 'l-t'],
      'jdbc->database': ['r-s', 'l-t'],
    },
  },
  compact: {
    order: ['application', 'entityManager', 'persistenceContext', 'jdbc', 'database'],
    handles: { 'entityManager->jdbc': ['r-s', 'r-t'] },
  },
  heights: { wide: 330 },
  fullWidth: true,
  minWidth: 900,
  nodeMeta: {
    application: { kind: 'business', stage: 1 },
    entityManager: { kind: 'persistence', stage: 2 },
    persistenceContext: { kind: 'persistence', stage: '2a' },
    jdbc: { kind: 'database', stage: 3 },
    database: { kind: 'database', stage: 4 },
  },
  lanes: [
    { id: 'application', label: 'SpringLab code', kind: 'business', nodes: ['application'] },
    { id: 'hibernate', label: 'JPA provider', caption: 'Hibernate', kind: 'persistence', nodes: ['entityManager', 'persistenceContext'] },
    { id: 'database', label: 'JDBC and MySQL', kind: 'database', nodes: ['jdbc', 'database'] },
  ],
  legend: { position: 'bottom-right' },
}

const sim = (id, title, nodes, edgeFrom, edgeTo, explanation) => ({
  id, title, nodes, edgeFrom, edgeTo, direction: 'REQUEST', provenance: 'CONCEPTUAL', explanation, evidence: [], sql: [], codeRefId: null,
})

/** A conceptual walkthrough of what an EntityManager does with a change. Nothing here was observed. */
export const SIMULATED_STEPS = [
  sim('call', 'A service method asks the EntityManager', ['application'], 'application', 'entityManager',
    'Inside a transaction, the service calls find, persist, merge or remove, directly or through a Spring Data repository.'),
  sim('track', 'The persistence context tracks the entity', ['entityManager', 'persistenceContext'], 'entityManager', 'persistenceContext',
    'A found or persisted entity becomes managed. The persistence context keeps it in the first-level cache and remembers the state it was loaded with.'),
  sim('change', 'Changing a managed entity prepares no SQL', ['persistenceContext'], null, null,
    'Calling a setter on a managed entity only changes the Java object. Hibernate learns about the change when it compares the entity with its snapshot at flush.'),
  sim('flush', 'Flush: dirty checking becomes SQL', ['entityManager', 'jdbc'], 'entityManager', 'jdbc',
    'Before a query that needs it, on flush() and before commit, Hibernate compares managed entities with their snapshots and prepares INSERT, UPDATE and DELETE statements in an order that respects foreign keys.'),
  sim('send', 'JDBC sends the statement to MySQL', ['jdbc', 'database'], 'jdbc', 'database',
    'The statement text, with placeholders, is prepared through JDBC and executed by MySQL inside the open transaction. Other transactions do not see the change yet.'),
  sim('commit', 'Commit makes the change permanent', ['database'], null, null,
    'The transaction manager commits. Only now do other transactions see the change, and the persistence context ends with the transaction.'),
]

export const SIMULATED_SCENARIO = {
  id: 'jpa-flow-simulated',
  title: 'What an EntityManager does with a change (conceptual walkthrough)',
  mode: 'SIMULATED',
  nodes: FLOW_NODES,
  edges: FLOW_EDGES,
  layout: FLOW_LAYOUT,
  steps: SIMULATED_STEPS,
}

/** The LIVE scenario for a persistence-context experiment: the server's steps on the same fixed diagram. */
export function liveFlowScenario(run, title) {
  return { id: `jpa-live-${run.experimentId}`, title, mode: 'LIVE', nodes: FLOW_NODES, edges: FLOW_EDGES, layout: FLOW_LAYOUT, steps: run.steps }
}

// ------------------------------------------------------------------------------------ explanations that are not run

/** Behaviour that cannot be executed from the browser or needs a different mapping. Shown clearly as SIMULATED. */
export const SIMULATED_NOTES = [
  {
    id: 'cascade-types',
    title: 'The five cascade types',
    body: 'PERSIST: persist(parent) also persists the children. MERGE: merge(parent) also merges the children. REMOVE: remove(parent) also removes the children. REFRESH: refresh(parent) also re-reads the children from the database. DETACH: detach(parent) also detaches the children. ALL: all five. The lab runs PERSIST and REMOVE (through ALL); MERGE, REFRESH and DETACH are explained here only.',
  },
  {
    id: 'unidirectional',
    title: 'A unidirectional variant',
    body: 'Removing Department.employees would leave only Employee.department: a unidirectional @ManyToOne with exactly the same tables and foreign key. mappedBy only adds the second Java reference. A unidirectional @OneToMany (a collection with no @ManyToOne on the other side) is a different mapping: without mappedBy it either needs a join table or a @JoinColumn on the collection, and Hibernate then manages the foreign key from the parent side with extra UPDATE statements. This lab does not run that variant.',
  },
  {
    id: 'multiple-bag',
    title: 'MultipleBagFetchException',
    body: 'Fetching two List collections (bags) of one entity in a single join fetch makes Hibernate throw MultipleBagFetchException, because the combined rows cannot be told apart reliably. Common remedies: fetch one collection per query, use Set, or enable batch fetching. The lab\'s two-collection experiment uses a List and a Set, which is allowed, and shows the row multiplication instead.',
  },
  {
    id: 'open-in-view',
    title: 'What spring.jpa.open-in-view=true would change',
    body: 'With Open Session in View the persistence context stays open until the web response is written, so lazy loading also works in controllers and during JSON serialisation, at the cost of hidden queries and resources held for the whole request. SpringLab sets it to false, so the lazy-after-close experiment fails the way a service-layer bug would.',
  },
  {
    id: 'sequence',
    title: 'IDENTITY versus a sequence',
    body: 'The lab\'s entities use IDENTITY because MySQL generates AUTO_INCREMENT keys, so persist() must run the INSERT immediately to learn the id. With a sequence-based generator the next value can be fetched without inserting, and Hibernate may delay the INSERT until flush. MySQL has no native sequences, which is why this lab cannot show that variant live.',
  },
]

export const STATIC_ASSUMPTIONS = [
  'The lab runs the application\'s own Hibernate and Spring Data JPA against the local MySQL database; nothing is mocked or replayed.',
  'Every experiment uses temporary rows in the jpa_lab_* tables that it creates and removes itself. No SpringLab application table is read or changed.',
  'SQL shown is the text Hibernate handed to JDBC, with ? placeholders. It is not proof of how MySQL executed it, and bound values are never shown.',
  'Rows shown were read back with plain JDBC. Rows read inside the experiment\'s transaction are visible to that transaction only until it commits.',
  'Statement counts are measured: they are the number of statements Hibernate prepared during each step, and the automated tests compare them with Hibernate\'s own statistics.',
  'IDENTITY keys, MySQL 8 and InnoDB behaviour (foreign key enforcement, REPEATABLE READ visibility) are part of what the experiments show; other databases or generators can behave differently.',
  'Lazy-loading examples depend on spring.jpa.open-in-view being false, which is how SpringLab is configured.',
]

/** Key points for the Explain stage, each linked to the experiments that show it. */
export const TAKEAWAYS = [
  { text: 'The owning side decides the foreign key. Changing only the inverse (mappedBy) collection never changes a foreign key.', experiments: ['owning-owner-only', 'owning-inverse-only', 'owning-helper-both', 'm2m-inverse-only'] },
  { text: 'Cascade and orphanRemoval are different: cascade follows operations on the parent; orphanRemoval deletes a child removed from the collection.', experiments: ['remove-child-no-orphan', 'remove-child-orphan', 'remove-parent-cascade-all'] },
  { text: 'The database enforces its own constraints (UNIQUE, FOREIGN KEY) regardless of what the mapping hopes.', experiments: ['one-to-one-fk', 'remove-parent-no-cascade'] },
  { text: 'Lazy is a default, not a cure: the same data can cost one statement or N+1 depending on the query.', experiments: ['lazy-not-accessed', 'lazy-in-transaction', 'n-plus-one'] },
  { text: 'EAGER does not avoid N+1: JPQL ignores the fetch type and Hibernate then loads the missing associations one by one.', experiments: ['fetch-defaults', 'eager-n-plus-one'] },
  { text: 'JOIN FETCH and EntityGraph turn 1+N statements into one, but fetching several collections multiplies rows.', experiments: ['join-fetch', 'entity-graph', 'two-collections-cartesian'] },
  { text: 'Changing a managed entity needs no save(); the UPDATE happens at flush, and only commit makes it visible to others.', experiments: ['dirty-checking', 'entity-states', 'first-level-cache'] },
]

/** Entity state shown for each step of the entity-states run; the names are inferred from contains() and the id. */
export const ENTITY_STATE_BY_STEP = {
  new: 'TRANSIENT',
  persist: 'MANAGED',
  detach: 'DETACHED',
  merge: 'MANAGED (a copy)',
  remove: 'REMOVED (scheduled)',
  flush: 'REMOVED (DELETE sent)',
  commit: 'REMOVED (committed)',
}

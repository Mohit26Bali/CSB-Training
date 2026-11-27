-- Challenge Arena: questions for the JPA Relationship Lab (topic 'jpa-hibernate').
-- Written for Spring Boot 3.5, Jakarta Persistence 3.1 and Hibernate 6.6. Every question has exactly one correct option.

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'EASY', 'What does an object-relational mapper (ORM) such as Hibernate do?',
 'An ORM bridges the object model and the relational model. You work with entities and associations in Java; the ORM translates operations on them into SQL statements for the database. The database is still a relational database.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'It replaces the relational database with an in-memory object store', FALSE, 0), (@q, 'It maps Java classes and their relationships to database tables and generates the SQL needed to store and load them', TRUE, 1), (@q, 'It compiles SQL queries into Java bytecode at build time', FALSE, 2), (@q, 'It encrypts every entity before it is written to disk', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'EASY', 'What is the relationship between JPA (Jakarta Persistence) and Hibernate?',
 'Jakarta Persistence defines annotations such as @Entity and @OneToMany and the EntityManager API. A persistence provider implements it. Hibernate is the provider Spring Boot''s spring-boot-starter-data-jpa brings in; Spring Data JPA builds repositories on top of the specification.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'JPA is a specification (an API); Hibernate is one implementation of it that Spring Boot uses by default', TRUE, 0), (@q, 'JPA is a library that Hibernate depends on to run SQL', FALSE, 1), (@q, 'Hibernate is the specification and JPA is its reference implementation', FALSE, 2), (@q, 'They are two names for the same jar file', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'EASY', 'Which of these is required for a class annotated with @Entity?',
 'The Jakarta Persistence specification requires an @Id (or @EmbeddedId) and a no-arg constructor that is public or protected; the class must not be final. Serializable is only needed in special cases and setters are not required. Schema creation is a separate concern (SpringLab uses Flyway).',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'A public setter for every field', FALSE, 0), (@q, 'An implementation of java.io.Serializable', FALSE, 1), (@q, 'A table created with spring.jpa.hibernate.ddl-auto=create', FALSE, 2), (@q, 'An identifier property marked with @Id and a no-argument constructor that is public or protected', TRUE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'EASY', 'Department 1 --- * Employee: in the database, where does the foreign key column normally live?',
 'A foreign key always sits on the ''many'' side: each employee row stores the id of its one department. A department row cannot hold a variable number of employee ids in a single relational column. That is also why @ManyToOne is normally the owning side.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'In the department table, as a column that lists its employee ids', FALSE, 0), (@q, 'In a separate join table that every one-to-many relationship needs', FALSE, 1), (@q, 'In the employee table, as a column that references the department''s primary key', TRUE, 2), (@q, 'In both tables, so each side can find the other', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'In a bidirectional @OneToMany / @ManyToOne association, which side is the owning side?',
 'The owning side is the one that maps the foreign key (here Employee.department with @JoinColumn). The other side declares mappedBy and is the inverse side. When Hibernate writes the association it reads the owning side only.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'The @OneToMany side, because the parent is always the owner', FALSE, 0), (@q, 'The side without mappedBy (normally the @ManyToOne side), because its field maps the foreign key column', TRUE, 1), (@q, 'Whichever side was persisted first', FALSE, 2), (@q, 'Both sides own the relationship equally', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'What does mappedBy = "department" on Department.employees tell the persistence provider?',
 'mappedBy names the field on the other entity that owns the mapping. It declares this side as the inverse side: it exists for navigation and queries, but changes to it are not used to write the foreign key. It does not influence fetching or cascading.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'That the association is owned by the Employee.department field, so this collection is the inverse side and does not write the foreign key', TRUE, 0), (@q, 'That the employees table must be named department', FALSE, 1), (@q, 'That the collection is fetched eagerly', FALSE, 2), (@q, 'That deleting a department deletes its employees', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'You add an Employee to department.getEmployees() (the mappedBy side) but never call employee.setDepartment(department). You then persist the employee and flush. What is stored in department_id?',
 'Hibernate builds the INSERT from the owning side. Employee.department is null, so department_id is NULL even though the department''s list in memory contains the employee. This is why bidirectional associations should be changed through a helper method that updates both sides.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'The department''s id, because the collection contains the employee', FALSE, 0), (@q, 'An exception is thrown because the two sides are inconsistent', FALSE, 1), (@q, 'The id of the first department in the table', FALSE, 2), (@q, 'NULL, because only the owning side Employee.department is used to write the foreign key', TRUE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'Project.tasks is @OneToMany(mappedBy = "project", cascade = CascadeType.ALL) without orphanRemoval. You remove a task from project.getTasks() and flush. What happens to the task row?',
 'CascadeType.REMOVE applies when the PARENT is removed. Removing a child from the collection is the job of orphanRemoval, which is not enabled here. The collection is also the inverse side (mappedBy), so Hibernate writes nothing for the change.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'Hibernate prepares a DELETE for the task because cascade ALL includes REMOVE', FALSE, 0), (@q, 'Hibernate sets the task''s project_id to NULL', FALSE, 1), (@q, 'Nothing: no statement is prepared and the row stays, because cascade does not delete children removed from the collection', TRUE, 2), (@q, 'Hibernate throws an exception at flush', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'What does orphanRemoval = true on a @OneToMany collection add?',
 'orphanRemoval treats a child that no longer belongs to any parent collection as an orphan and deletes it at flush. It is a different mechanism from cascade REMOVE, which reacts to removing the parent. It applies to one-to-one and one-to-many associations whose child is exclusively owned by the parent.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'Children are loaded eagerly together with the parent', FALSE, 0), (@q, 'A child that is removed from the parent''s collection is scheduled for deletion', TRUE, 1), (@q, 'The parent is deleted when its last child is deleted', FALSE, 2), (@q, 'Removed children are archived in a separate table', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'Why is CascadeType.REMOVE (or ALL) on a @ManyToMany association usually a mistake?',
 'In a many-to-many, the related entities are shared. Deleting a student with cascade REMOVE would delete the courses the student attends, even if other students attend them (or the database would reject it). Only the join table rows should disappear.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'Removing one entity would also remove the shared entities on the other side, which other entities may still reference', TRUE, 0), (@q, 'Join tables cannot be deleted', FALSE, 1), (@q, 'REMOVE is not allowed on collections of type Set', FALSE, 2), (@q, 'It makes the association unidirectional', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'EASY', 'What is the Jakarta Persistence default fetch type of a @ManyToOne association?',
 'By specification @ManyToOne and @OneToOne default to EAGER, while @OneToMany and @ManyToMany default to LAZY. Many projects set @ManyToOne(fetch = FetchType.LAZY) explicitly to avoid loading associations that are not needed.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'LAZY', FALSE, 0), (@q, 'There is no default; the attribute is required', FALSE, 1), (@q, 'It depends on the database dialect', FALSE, 2), (@q, 'EAGER', TRUE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'EASY', 'What is the default fetch type of a @OneToMany collection?',
 'Collection-valued associations (@OneToMany, @ManyToMany) default to LAZY: Hibernate puts an uninitialised collection wrapper in the entity and runs the SELECT the first time the collection is used.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'EAGER', FALSE, 0), (@q, 'BATCH', FALSE, 1), (@q, 'LAZY', TRUE, 2), (@q, 'It is loaded only when the entity is merged', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'When does a LazyInitializationException occur?',
 'A lazy collection or proxy needs an open session to run its SELECT. Outside the transaction there is none, so Hibernate throws the exception instead of loading. The fix is to load what you need inside the service layer (for example with a fetch join), not to make everything eager.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'When two threads load the same entity at the same time', FALSE, 0), (@q, 'When a lazy association is accessed after the persistence context (session) that loaded the entity has been closed', TRUE, 1), (@q, 'When an entity has no @Id', FALSE, 2), (@q, 'When a lazy association is accessed inside a transaction', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'What does spring.jpa.open-in-view=true change, and what is the usual argument for turning it off?',
 'Open Session in View lets lazy associations load anywhere during the request, including while JSON is written. That hides queries outside the service layer (and N+1 problems) and keeps resources in use longer. SpringLab sets it to false, so lazy loading must happen inside a service method.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'It keeps the persistence context open for the whole web request so lazy loading also works in controllers; turning it off keeps data access inside the service layer and avoids hidden queries from outside it', TRUE, 0), (@q, 'It makes every association eager', FALSE, 1), (@q, 'It opens a second database connection for each view', FALSE, 2), (@q, 'It disables transactions for read-only requests', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'What is the N+1 query problem?',
 'Loading the parents is one statement. Touching a lazy collection on each of the N parents runs one more SELECT per parent, so the number of statements grows with N. The code looks harmless in Java; the SQL log shows the cost.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'A query that returns N+1 rows instead of N', FALSE, 0), (@q, 'A database index that is missing on N columns', FALSE, 1), (@q, 'A transaction that is committed N+1 times', FALSE, 2), (@q, 'One query loads N parent entities and then one extra query per parent loads its lazy association, so N+1 statements run', TRUE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'HARD', 'Which technique loads departments together with their employees in a single SELECT?',
 'A fetch join, or an @EntityGraph on the query method, tells Hibernate to fetch the association in the same statement. Looping and calling size() is the N+1 pattern itself; LAZY is the default for collections; clear() only empties the persistence context.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'Calling department.getEmployees().size() inside a loop', FALSE, 0), (@q, 'Marking Department.employees as LAZY', FALSE, 1), (@q, 'A JPQL fetch join (join fetch d.employees) or an EntityGraph that includes employees', TRUE, 2), (@q, 'Calling EntityManager.clear() before the query', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'HARD', 'A JPQL query tries to join fetch two different List (bag) collections of the same entity. What does Hibernate do?',
 'Hibernate refuses to fetch more than one bag in a single query because the cartesian product makes the duplicates impossible to separate reliably. Typical remedies are fetching one collection per query, using Set for collections, or batch fetching.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'It silently fetches only the first collection', FALSE, 0), (@q, 'It throws MultipleBagFetchException', TRUE, 1), (@q, 'It runs two queries automatically', FALSE, 2), (@q, 'It converts both lists to sets and succeeds', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'HARD', 'A parent has 3 employees and 4 rooms (two independent collections). A single query join fetches both. How many rows does the database return for that parent?',
 'Joining a parent to two independent child tables produces every combination of one child from each, so the row count is the product. This cartesian multiplication is why fetching several collections in one query is costly even though only one statement runs.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, '12 (3 x 4), because every employee is combined with every room', TRUE, 0), (@q, '7 (3 + 4)', FALSE, 1), (@q, '3', FALSE, 2), (@q, '4', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'Task.project is a @ManyToOne with the default fetch type (EAGER). You run the JPQL query select t from Task t. How many statements does Hibernate typically prepare when the tasks belong to 3 different projects?',
 'JPQL is translated as written; the fetch type does not add a join to it. Because the association is EAGER, Hibernate must then load each project and does so with extra SELECTs. EAGER is therefore not a cure for N+1. Use a fetch join or an EntityGraph where the data is needed.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'One: EAGER always joins the project', FALSE, 0), (@q, 'Two: one for tasks, one for all projects', FALSE, 1), (@q, 'Zero until a getter is called', FALSE, 2), (@q, 'Four: one for the tasks plus one for each missing project', TRUE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'In a transaction you load an Employee, call employee.setName("New") and never call save(). The transaction commits. What happens?',
 'A managed entity is compared with the snapshot taken when it was loaded. At flush (which happens before commit) Hibernate prepares an UPDATE for every difference. Calling save() on an already managed entity adds nothing.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'Nothing is saved because save() was not called', FALSE, 0), (@q, 'An exception is thrown because the entity was modified', FALSE, 1), (@q, 'Hibernate detects the change by dirty checking and prepares an UPDATE at flush, so the new name is saved', TRUE, 2), (@q, 'The change is saved only if the entity is also detached', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'What is the difference between flush() and commit?',
 'Flush synchronises the persistence context with the database, so the UPDATE or INSERT is executed, but the transaction can still be rolled back and other transactions do not see the change under the usual isolation levels. Commit makes it permanent.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'They are the same operation with two names', FALSE, 0), (@q, 'flush() sends pending changes to the database inside the open transaction; commit ends the transaction and makes the changes permanent and visible to other transactions', TRUE, 1), (@q, 'flush() makes changes visible to other transactions; commit only closes the session', FALSE, 2), (@q, 'commit sends the SQL and flush() clears the first-level cache', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'HARD', 'With @GeneratedValue(strategy = GenerationType.IDENTITY) on MySQL, when does EntityManager.persist() execute the INSERT?',
 'With IDENTITY the id is generated by the INSERT itself, so Hibernate must execute the statement during persist() to assign the id. With a sequence generator the INSERT can be delayed until flush. persist() therefore does not promise ''no SQL'' or ''immediate SQL'' in general: it depends on the generator.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'Immediately, because Hibernate needs the generated id and only the database can supply it', TRUE, 0), (@q, 'Only at commit, like every other statement', FALSE, 1), (@q, 'Never; persist() only changes the object''s state', FALSE, 2), (@q, 'Only when flush() is called explicitly', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'Within one persistence context you call em.find(Employee.class, 1L) twice. How many SELECT statements does Hibernate prepare?',
 'The persistence context is a first-level cache keyed by entity type and id. The second find() returns the managed instance already in it. A JPQL query would still run a SELECT, but it would map the row to the same instance.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'Two, because find() always queries the database', FALSE, 0), (@q, 'Zero, because find() uses the second-level cache', FALSE, 1), (@q, 'One per field of the entity', FALSE, 2), (@q, 'One: the second call is answered by the first-level cache and returns the same instance', TRUE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'HARD', 'What does EntityManager.merge(detachedEmployee) return?',
 'merge() loads the current state (usually with a SELECT), copies the detached state onto a managed instance and returns that instance. The argument itself is not made managed, so code must continue with the returned object.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'The same object, now re-attached to the persistence context', FALSE, 0), (@q, 'Always a new row with a new id', FALSE, 1), (@q, 'A managed copy that carries the detached object''s state; the object that was passed in stays detached', TRUE, 2), (@q, 'Nothing; merge returns void', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'Which annotation declares the join table of a many-to-many association, and on which side is it placed?',
 'The owning side of a @ManyToMany declares @JoinTable (name, joinColumns and inverseJoinColumns). The inverse side uses mappedBy and writes nothing to the join table. Associations that carry their own data should become an explicit association entity instead.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'jpa-hibernate';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, '@JoinColumn, on the inverse side', FALSE, 0), (@q, '@JoinTable, on the owning side', TRUE, 1), (@q, '@Table, on both sides', FALSE, 2), (@q, '@MappedBy, on the owning side', FALSE, 3);

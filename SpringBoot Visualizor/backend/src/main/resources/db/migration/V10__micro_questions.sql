-- Challenge Arena: the Microservices topic and its questions (for the Microservices Lab). Every question has exactly one correct option.
-- The questions cover the eight lab topics: monolith vs microservices, service communication, API gateway, service discovery, database per
-- service, distributed transactions (outbox and saga), resilience and observability. They describe SpringLab's simulator as it behaves
-- (a modular monolith with simulated services), not a deployed microservice system, and they do not describe Kafka, Eureka, Resilience4j or
-- OpenTelemetry. Written for the existing arena tables (V2); nothing about scoring or attempts changes.

INSERT INTO topics (name, slug, description) VALUES
    ('Microservices Architecture', 'microservices', 'Service boundaries, communication, gateway, discovery, data ownership, sagas and outbox, resilience and observability, as simulated in the Microservices Lab.');

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'EASY', 'In the monolith version of the order workflow, stock is reserved and the payment is charged inside one database transaction, and the charge then fails. What is the outcome?',
 'Inside one transaction the database makes the steps all-or-nothing: the reservation was never committed, so a rollback leaves nothing behind. This is exactly what is lost when the same steps are spread over services that each commit their own local transaction.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'The transaction is rolled back, so the reservation never becomes visible to anyone and no cleanup is needed', TRUE, 0), (@q, 'The reservation stays committed until a scheduled job notices the failed payment', FALSE, 1), (@q, 'The reservation is committed, and a compensating event releases it a moment later', FALSE, 2), (@q, 'The order is saved as failed while the reserved stock stays reduced for auditing', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'A team splits an application so that every class becomes its own service, hoping for independence. Which consequence follows most directly?',
 'A service boundary is a network boundary. Method calls gain latency and failure modes, and the all-or-nothing guarantee of a single transaction is gone. Splitting without a reason (independent change, scaling or ownership) adds these costs and buys nothing; the over-split experiment shows the calls and the lost transaction.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'The services can no longer share any library code, so every class must be rewritten', FALSE, 0), (@q, 'The database automatically becomes eventually consistent, which removes the need to think about failures', FALSE, 1), (@q, 'Calls that were in-process method calls become network calls that can fail or time out, and a workflow across them no longer has one transaction', TRUE, 2), (@q, 'Every service gains independent scaling for free, with no extra operational cost', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'HARD', 'Which statement about a modular monolith is accurate?',
 'A modular monolith keeps one deployable and cheap in-process calls but enforces boundaries: each module owns its tables and others use its contract. SpringLab is built this way, and its Microservices Lab is a simulation inside it: the services are classes in one JVM talking through an in-process ServiceBus, not separately deployed processes.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'Its modules run as separate processes that share a common library', FALSE, 0), (@q, 'It is deployed as one unit and can use one transaction, but its modules own their data and talk through contracts, which keeps the boundaries ready for extraction', TRUE, 1), (@q, 'It forbids database transactions, because modules must never share a connection', FALSE, 2), (@q, 'It is the same as an unstructured monolith with a different name', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'The Order service calls Payment and gets a TIMEOUT. What can it conclude?',
 'A timeout only says that no answer arrived in time. The request may have been lost, or processed with the reply lost. This is why retrying a TIMEOUT is only safe for an idempotent operation, and why a charge needs an idempotency key.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'The charge was not made, so it is safe to retry without any protection', FALSE, 0), (@q, 'The charge was made, so the order can be confirmed', FALSE, 1), (@q, 'The charge was made and then automatically rolled back by the network', FALSE, 2), (@q, 'Nothing certain: the charge may have been made or not, so it must not assume either', TRUE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'An event channel delivers each message at least once. A consumer receives the same message id twice. Which consumer design makes this harmless?',
 'At-least-once delivery means duplicates are possible, for example when a delivery is repeated because an acknowledgement was lost. The reliable defence is an idempotent consumer: the processed id and the effect commit together, so a repeat is recognised. The lab shows a naive consumer applying the effect twice, and an idempotent one applying it once.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'Ignore every message from a sender that has already sent one', FALSE, 0), (@q, 'Record the message id together with the effect in one local transaction, and skip a message whose id was already recorded', TRUE, 1), (@q, 'Switch retries off in the channel, which guarantees exactly one delivery', FALSE, 2), (@q, 'Delete the message from the channel as soon as it is read, so it cannot come back', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'A payment is declined because of insufficient funds, and the Payment service answers with a BUSINESS_FAILURE. How is this treated by the resilience rules in the lab?',
 'The service worked and gave a definite refusal; repeating the call would give the same refusal. Only outcomes that say the dependency had a problem (unavailable, timeout, technical error, and so on) are candidates for retry or for opening a breaker.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'It is retried with backoff, because the funds may arrive in the meantime', FALSE, 0), (@q, 'It counts toward opening the circuit breaker, like a timeout', FALSE, 1), (@q, 'It is treated as a TECHNICAL_ERROR and the bulkhead is released', FALSE, 2), (@q, 'It is a valid answer, not a dependency problem: it is not retried and does not count as a failure of the service', TRUE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'HARD', 'The Notification service is down. Placing an order still succeeds when notification is done by publishing an event, but not when the Order service calls Notification synchronously and requires an answer. Why?',
 'In the synchronous design the notification is on the critical path of the request. With an event the publisher does not wait for the consumer, so the consumer being down delays the notification instead of stopping the order. The price is that the notification arrives later and the system is only eventually consistent.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'The event decouples the order from the consumer in time: the order commits without waiting, and the notification is handled when the consumer is back', TRUE, 0), (@q, 'The event channel contacts the Notification service again and again until it answers, and the order waits for that', FALSE, 1), (@q, 'Events skip the Notification service, so it is not informed at all', FALSE, 2), (@q, 'Asynchronous messages are exempt from the failure of the consumer, so a delivery to a down service is impossible', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'EASY', 'What is the role of an API gateway in front of several services?',
 'A gateway sits at the edge: it checks credentials, can limit the request rate and forwards each request to the service that owns the route. It does not own business data and does not make the services reliable. In SpringLab the gateway is a simulated component.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'It stores the data for all the services in one table', FALSE, 0), (@q, 'It replaces the services by combining them into one program', FALSE, 1), (@q, 'It is the single entry point that authenticates the request, applies limits and routes it to the right service', TRUE, 2), (@q, 'It guarantees that no service behind it can ever fail', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'In the rate-limit experiment the limit is 3 requests per window and a client sends 5 requests inside the same window. What does the client see?',
 'The gateway counts requests per client in a window; once the limit is reached it answers 429 RATE_LIMIT_EXCEEDED itself, so the protected services see no traffic for the rejected requests. The counter is a fixed window on the simulated clock.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'All 5 are forwarded, and the services answer 429 themselves', FALSE, 0), (@q, 'The first 2 are rejected and the last 3 are forwarded', FALSE, 1), (@q, 'The gateway queues all 5 and forwards them in the next window', FALSE, 2), (@q, 'The first 3 are forwarded; the rest are answered by the gateway with 429 and never reach a service', TRUE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'HARD', 'Compare two failures: (a) the gateway is down, (b) the Inventory service is down. Which statement is correct?',
 'A single gateway is a single point of failure for everything behind it, unless it is replicated. A failed service only affects the routes that depend on it. The gateway failure experiment contrasts the two.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'In (a) every client request fails even though the services are healthy; in (b) only the routes that need Inventory are affected', TRUE, 0), (@q, 'In (a) only the first request fails, because the gateway restarts itself; in (b) every route fails', FALSE, 1), (@q, 'Both failures affect exactly the same requests, because the gateway forwards everything', FALSE, 2), (@q, 'In (a) the clients bypass the gateway automatically and reach the services directly', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'EASY', 'What problem does service discovery solve?',
 'In an environment where instances start, stop and move, a caller cannot hard-code where a service runs. A registry maps a service name to the instances that are currently registered and healthy. In SpringLab the registry is a simulated component, not a deployed one.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'It encrypts the traffic between services', FALSE, 0), (@q, 'It converts events into synchronous calls', FALSE, 1), (@q, 'Instances come and go and change address, so callers look a service up by name instead of using a fixed address', TRUE, 2), (@q, 'It decides which database table a service may read', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'An instance crashes without telling the registry. Right afterwards, a caller asks the registry for that service. What may happen?',
 'A registry only knows what instances tell it. A silent crash sends no deregistration and no further heartbeats, so the entry lives until it ages out. Until then the registry can recommend a dead instance, which is what the stale-registration experiment shows.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'The registry always notices the crash at once and never returns it', FALSE, 0), (@q, 'The registry may still return the dead instance, until its registration expires because no heartbeat arrived', TRUE, 1), (@q, 'The registry returns an error for the whole service until an administrator removes the instance', FALSE, 2), (@q, 'The call is rerouted by the network without involving the registry', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'HARD', 'A team shortens the heartbeat expiry (TTL) to reduce the window in which a dead instance is returned. What trade-off should they expect?',
 'Any expiry-based scheme has a window between a crash and the entry expiring; a shorter TTL narrows it but makes false removals of slow instances more likely, and it never reaches zero. Callers still need timeouts and a way to try another instance.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'No trade-off: a shorter TTL removes the window completely', FALSE, 0), (@q, 'Heartbeats stop being needed, because the registry can read the instances'' memory', FALSE, 1), (@q, 'A slow but alive instance may be dropped by mistake because its heartbeat is late, and callers should still handle a failed call by trying another instance', TRUE, 2), (@q, 'The registry stops being a single source of truth for all callers, so it can no longer be used', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'EASY', 'What does "database per service" mean?',
 'Ownership is about who may read and write the data, not about the product. Others ask the owner or react to its events, so the owner can change its schema without breaking them.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'Every service runs a copy of the whole database and keeps it synchronised', FALSE, 0), (@q, 'Each service exclusively owns its data, and other services get it only through that service''s contract (an API or events)', TRUE, 1), (@q, 'All services share one set of tables but use different user names', FALSE, 2), (@q, 'Every service must use a different database product', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'Two services keep their data in different databases. Can an Order table have a foreign key to a Product table that belongs to the Product service?',
 'Constraints and joins are local to one database. Across services an id is just a reference; keeping it meaningful (for example by validating through the Product contract or by storing a snapshot) is the application''s job. The lab keeps the foreign keys inside each service''s own tables.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'Yes, and the database guarantees the product exists, as it would in one schema', FALSE, 0), (@q, 'Yes, if both tables use the same character set', FALSE, 1), (@q, 'No, because services may never store another service''s id at all', FALSE, 2), (@q, 'No. A foreign key cannot span the boundary; the Order service stores the product id and relies on the Product contract and application-level rules for consistency', TRUE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'The Inventory service renames a column. A second service reads that table directly with its own SQL, and starts to fail. Which principle was broken?',
 'Direct access to another service''s tables couples the services at the schema level without any contract. The shared-database experiment shows the coupling; the contract is what lets the owner change its internals.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'The table was a shared database: the schema had become an undeclared API that Inventory could not change safely', TRUE, 0), (@q, 'The principle of caching, because the second service had no cache', FALSE, 1), (@q, 'The principle of idempotency, because the query was repeated', FALSE, 2), (@q, 'No principle. Schema changes always break readers in every design', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'HARD', 'In the SpringLab simulator all service tables live in one MySQL schema. What kind of isolation does the ownership guard provide?',
 'The simulator uses real MySQL, but one schema. Ownership is enforced by naming, source rules and a runtime guard around the connection, so a forbidden statement is refused and recorded. That demonstrates the rule; it does not make the data physically separate, which a real deployment would do with separate databases.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'Logical isolation: each service owns its own tables, and a runtime guard refuses statements that touch another service''s tables; it is not physical separation', TRUE, 0), (@q, 'Physical isolation, because every service gets its own server', FALSE, 1), (@q, 'No isolation. Services can read each other''s tables freely and the lab only warns', FALSE, 2), (@q, 'Cryptographic isolation, because each service encrypts its tables with its own key', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'EASY', 'What is a saga?',
 'Without a transaction spanning services, each service commits its own step. If a later step fails, the earlier committed steps are reversed by new business operations, the compensations. The saga restores business consistency, not the database state.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'One large database transaction that locks the tables of every service', FALSE, 0), (@q, 'A cache that stores the results of earlier orders', FALSE, 1), (@q, 'A protocol that makes every network call succeed', FALSE, 2), (@q, 'A sequence of local transactions across services, where a failed step is answered by compensating operations for the steps already completed', TRUE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'A service commits an order to its database and then publishes the OrderPlaced event; the process stops between the two. What is the result?',
 'Committing and publishing are two writes to two systems, and no transaction covers both, so a failure between them leaves them inconsistent. That window is the dual-write gap.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'The order is rolled back automatically, so the data stays consistent', FALSE, 0), (@q, 'The order exists but the event was never published, so other services never learn about it', TRUE, 1), (@q, 'The event is published when the process restarts, with no extra mechanism', FALSE, 2), (@q, 'The event is published, but the order is missing', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'With the transactional outbox, the order and its event are written in the same local transaction, and a relay publishes the event later. What happens if the transaction rolls back?',
 'The outbox row is part of the same local transaction as the business data, so the two commit or vanish together. A relay then reads committed rows only. This closes the dual-write gap for the data and the event.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'The order is gone, but the relay still publishes the event', FALSE, 0), (@q, 'The event is kept and the relay publishes it with the status failed', FALSE, 1), (@q, 'Neither the order nor the event exists, so nothing is published', TRUE, 2), (@q, 'The order is kept and only the event is rolled back', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'HARD', 'The outbox relay publishes an event, and then fails before marking the row as published. The relay runs again. What does a correct design assume?',
 'Publishing and marking the row are two steps; a failure between them is resolved by publishing again, so duplicates are possible. The outbox gives at-least-once delivery. Exactly-once effects come from the consumer''s idempotence, not from the channel.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'The channel will remove the duplicate itself, so delivery is exactly-once', FALSE, 0), (@q, 'The event will be published a second time, so the delivery is at-least-once and consumers must be idempotent', TRUE, 1), (@q, 'The first publication is cancelled, because the row was never marked', FALSE, 2), (@q, 'The row is deleted without publishing, to prevent a duplicate', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'In the orchestrated saga, the payment fails after the stock has been reserved. Which statement is accurate?',
 'The reservation was committed by Inventory in its own local transaction and may have been seen by others. A saga cannot roll it back; it compensates with a release, and the release leaves history (a RELEASED reservation).',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'The database rolls back the reservation, because the saga is one transaction', FALSE, 0), (@q, 'The reservation disappears when the order is deleted, which is automatic', FALSE, 1), (@q, 'The orchestrator asks Inventory to release the reservation, a new business operation, because the reservation was already committed and cannot be rolled back', TRUE, 2), (@q, 'Nothing needs to happen, because stock is only reserved after payment', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'HARD', 'What is the central difference between saga orchestration and choreography?',
 'An orchestrator makes the flow easy to follow and change in one place, at the price of coupling to every participant and of being critical itself. Choreography removes the coordinator, but the overall flow is harder to see. Both use compensations for a failed step.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'In orchestration one coordinator holds the flow and tells services what to do; in choreography each service reacts to events, and the flow exists only in their combined behaviour', TRUE, 0), (@q, 'Orchestration needs no persistence; choreography needs a database', FALSE, 1), (@q, 'Orchestration uses events only, and choreography uses synchronous calls only', FALSE, 2), (@q, 'In choreography one service holds the saga log of all the others', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'HARD', 'In the orchestrated saga, a compensation (the stock release) itself fails and the saga ends as COMPENSATION_FAILED. What happens next in the lab?',
 'A compensation is an ordinary operation and can fail. The lab does not claim automatic recovery: the saga log shows COMPENSATION_FAILED, and an explicit resume step performs the missing release later. A real system needs a durable log and a retrying process, which is a design decision rather than a given.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'The orchestrator retries forever on its own until the release succeeds', FALSE, 0), (@q, 'The database rolls back the whole saga, including the committed steps', FALSE, 1), (@q, 'The saga is marked completed, because compensations are optional', FALSE, 2), (@q, 'It stays that way until something acts; the lab resumes the compensation explicitly once Inventory is back, and nothing recovers it automatically', TRUE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'EASY', 'Why are retries limited to a small number of attempts, with a wait between them that grows?',
 'Bounded attempts stop a failing call from running forever, and a growing wait gives the dependency time to recover instead of hammering it. In the lab the wait is deterministic: 100 ms, doubling each attempt, capped at 800 ms, on the simulated clock.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'Retries are not allowed in distributed systems', FALSE, 0), (@q, 'A longer wait makes the second attempt succeed with certainty', FALSE, 1), (@q, 'The wait is needed only to make the logs easier to read', FALSE, 2), (@q, 'Unlimited immediate retries add load to a service that is already struggling, so attempts are bounded and backed off', TRUE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'A client retries a charge after a TIMEOUT. Without an idempotency key the first attempt had in fact succeeded. What is the risk, and what prevents it?',
 'A timeout leaves the outcome unknown, so a retry of a non-idempotent operation can duplicate it. The caller sends the same key with every attempt, and Payment stores the result under that key and returns it for the repeat instead of charging again.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'The charge is lost; nothing prevents it', FALSE, 0), (@q, 'The customer may be charged twice; an idempotency key lets Payment recognise the repeat and return the first result', TRUE, 1), (@q, 'The second charge is blocked by the circuit breaker, which remembers the first', FALSE, 2), (@q, 'The customer is charged once because TIMEOUT always means no work was done', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'A circuit breaker is CLOSED and the failure threshold is 3. A dependency fails three times in a row. What happens to the next call?',
 'After the threshold of consecutive failures the breaker opens and rejects calls without contacting the dependency (the outcome is CIRCUIT_OPEN, optionally answered by a fallback). After the open duration it lets one probe call through in HALF_OPEN.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'The call is sent as usual, because a breaker only counts failures for the log', FALSE, 0), (@q, 'The breaker moves to HALF_OPEN and sends every waiting call at once', FALSE, 1), (@q, 'The breaker is OPEN, so the call is refused locally and the dependency is not contacted', TRUE, 2), (@q, 'The breaker closes, because three failures prove that the dependency is healthy', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'HARD', 'A breaker is HALF_OPEN and allows one probe call. The probe fails. What does the breaker do?',
 'HALF_OPEN is a test: one call is allowed; if it succeeds the breaker closes and the count resets, if it fails the breaker reopens and waits again for the open duration. Other calls during the probe are refused.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'It returns to OPEN and starts a new open period before the next probe', TRUE, 0), (@q, 'It stays HALF_OPEN and lets the next calls through as probes', FALSE, 1), (@q, 'It moves to CLOSED, because a probe was made', FALSE, 2), (@q, 'It resets its failure count and stays HALF_OPEN', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'HARD', 'A client retries up to 3 attempts, and the Order service it calls also retries its call to Payment up to 3 attempts. Payment is down. How many attempts can reach Payment for one client request?',
 'Each client attempt makes the Order service run its own full set of attempts, so 3 x 3 = 9. Retrying at every layer amplifies the load on a dependency that is already failing; retries are usually kept at one layer and combined with a breaker.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'Up to 6, because the attempts of the two layers add up', FALSE, 0), (@q, 'Exactly 3, because only the outermost layer retries', FALSE, 1), (@q, 'Up to 9, because the attempts of the two layers multiply', TRUE, 2), (@q, 'Exactly 1, because the second layer cancels the retries of the first', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'What does a bulkhead protect against?',
 'A bulkhead limits how much of the shared capacity (threads, connections, permits) calls to one dependency may use. With separate permits per service, a stuck Payment cannot take all the permits and starve Inventory and Product calls.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'One slow or saturated dependency using up the capacity that calls to other dependencies need', TRUE, 0), (@q, 'The loss of data when the database restarts', FALSE, 1), (@q, 'Duplicate delivery of events', FALSE, 2), (@q, 'Unauthorised access to a service', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'EASY', 'What is a distributed trace?',
 'Each call is recorded as a span with its parent, caller, callee and timing; the correlation id ties the spans of one request together across services. The lab''s tracing is its own simulation of this idea, not OpenTelemetry.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'A copy of the database taken when the request arrived', FALSE, 0), (@q, 'A list of all errors of one service for a day', FALSE, 1), (@q, 'An encrypted copy of the request body', FALSE, 2), (@q, 'The set of spans for the calls that served one request, linked by a shared correlation id and by parent-child relations', TRUE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'In a trace, the Order span took 300 ms in total, and its child span for the Product call took 250 ms. How is the time best read?',
 'A span''s duration includes its children. Self time is the span''s duration minus the time of its child spans, so it points at where the time was actually spent. Adding parent and child durations counts the same time twice.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'Order spent all 300 ms of its own time, so Order is the bottleneck', FALSE, 0), (@q, 'About 50 ms were spent in Order itself (its self time), and the slowness is mostly in the Product call', TRUE, 1), (@q, 'Product spent 550 ms, since both spans are added up', FALSE, 2), (@q, 'Nothing can be said, because a parent span says nothing about its children', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'MEDIUM', 'A service does not pass the correlation id on to the services it calls. What is lost?',
 'The correlation id is what connects the work of different services to one request. Without it each service still works, but the trace has gaps and the downstream work shows up as separate, unrelated traces.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'The request is rejected by every downstream service', FALSE, 0), (@q, 'The downstream spans cannot be tied to the request, so the trace breaks into unrelated pieces', TRUE, 1), (@q, 'The downstream services run but no longer write any data', FALSE, 2), (@q, 'Only the response time becomes longer', FALSE, 3);

INSERT INTO questions (topic_id, difficulty, question_text, explanation, active, created_by, created_at, updated_at, version)
SELECT id, 'HARD', 'The trace table of an experiment shows "simulated ms" and "measured µs" columns. Which statement is accurate?',
 'The lab never sleeps: delays, timeouts and backoff are advanced on a logical clock (simulated) and are what make the experiments deterministic. The measured value is real time spent in the JVM running the handler. Neither is a network measurement, because there is no network between the simulated services.',
 TRUE, NULL, NOW(6), NOW(6), 0 FROM topics WHERE slug = 'microservices';
SET @q = LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, correct, sort_order) VALUES
 (@q, 'Both columns are the real network latency between two machines', FALSE, 0), (@q, 'Measured microseconds are derived from the simulated milliseconds by a fixed ratio', FALSE, 1), (@q, 'Simulated milliseconds are measured with a stopwatch, and microseconds are estimated', FALSE, 2), (@q, 'Simulated milliseconds come from the lab''s logical clock; measured microseconds are real elapsed time of the in-process handler in the JVM, and the two must not be mixed', TRUE, 3);

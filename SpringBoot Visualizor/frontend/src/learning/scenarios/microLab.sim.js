/**
 * Microservices Lab: the module's authored content and the diagram. Observations (calls, SQL, evidence, spans) never appear here: they come
 * from runs on the server (POST /api/lab/microservices/runs/.../execute). What IS authored here is explanation, layout, and the SIMULATED
 * (conceptual) walkthroughs, which carry no observation and are labelled as such.
 *
 * The whole lab runs in ONE JVM against ONE MySQL schema. Every explanation says what is real (JVM method calls, MySQL transactions and
 * constraints, Semaphore permits, our own state machines) and what is simulated (the network, latency, the broker, the gateway, the
 * registry, tracing propagation, independent deployment).
 */
import { edgeOverlay, enrichSteps, nodeOverlay } from '../schema/microRun'

export const MODULE = Object.freeze({
  id: 'microservices',
  title: 'Microservices Architecture Lab',
  topicSlug: 'microservices', // Challenge Arena topic used by "Test yourself" (the questions arrive with Stage D)
  summary:
    'Run real service-to-service workflows against MySQL inside one application: see how calls, events, local transactions, outbox publishing and saga compensation differ, and how distributed systems fail.',
})

// ---------------------------------------------------------------------------------------------------------------- what is real

export const REALITY = Object.freeze({
  real: [
    'Every service call is a real Java method call through a typed service bus, with its own handler code.',
    'Every service writes to its own tables with real SQL and its own LOCAL MySQL transaction: commits, rollbacks, unique keys, foreign keys and error codes are MySQL\'s own.',
    'Bulkhead permits are real Semaphores; the circuit breaker, retry and idempotency code are SpringLab\'s own deterministic state machines.',
    'Each run has its own rows, its own clock and its own state; nothing is shared between users.',
  ],
  simulated: [
    'The network: calls are method calls, nothing is sent or serialised, and no packet can be lost. Lost requests, slow responses and outages are injected.',
    'Time: latency and timeouts run on a logical clock (milliseconds are simulated), so a run is fast and repeatable. Measured microseconds are the real cost of the in-process call.',
    'The event channel is an in-memory list, not Kafka or RabbitMQ. The gateway and the service registry are small models, not production gateways or Eureka; addresses are labels.',
    'Tracing is an in-process simulation of spans and correlation ids, not OpenTelemetry. All "services" run in one JVM and share one MySQL schema: ownership is logical, not physical.',
  ],
})

// ---------------------------------------------------------------------------------------------------------------- the diagram

const NODES = [
  { id: 'client', label: 'Client', sublabel: 'browser or mobile app', description: 'The caller outside the system.' },
  { id: 'gateway', label: 'API Gateway', sublabel: 'edge: auth, limit, route', description: 'A simulated, in-process model of a gateway; not a production gateway.' },
  { id: 'broker', label: 'Event channel', sublabel: 'simulated, in-memory', description: 'A simulated message channel. Not Kafka or RabbitMQ.' },
  { id: 'user', label: 'User service', sublabel: 'customers', description: 'Owns the customers and their credit limit.' },
  { id: 'product', label: 'Product service', sublabel: 'catalogue', description: 'Owns products and prices.' },
  { id: 'inventory', label: 'Inventory service', sublabel: 'stock, reservations', description: 'Owns stock and reservations.' },
  { id: 'order', label: 'Order service', sublabel: 'orders, saga, outbox', description: 'Owns orders, the saga log and the outbox.' },
  { id: 'payment', label: 'Payment service', sublabel: 'payments, refunds', description: 'Owns payments. Holds an order reference and an amount only.' },
  { id: 'notification', label: 'Notification service', sublabel: 'messages, processed ids', description: 'Owns notifications and the ids of events it has processed.' },
  { id: 'registry', label: 'Service registry', sublabel: 'instances, health', description: 'A simulated registry: a table plus code, not Eureka.' },
  { id: 'user-db', label: 'User tables', sublabel: 'micro_lab_users', description: 'Tables owned by the User service.' },
  { id: 'product-db', label: 'Product tables', sublabel: 'micro_lab_products', description: 'Tables owned by the Product service.' },
  { id: 'inventory-db', label: 'Inventory tables', sublabel: 'stock, reservations', description: 'Tables owned by the Inventory service.' },
  { id: 'order-db', label: 'Order tables', sublabel: 'orders, saga, outbox', description: 'Tables owned by the Order service.' },
  { id: 'payment-db', label: 'Payment tables', sublabel: 'micro_lab_payments', description: 'Tables owned by the Payment service.' },
  { id: 'notification-db', label: 'Notification tables', sublabel: 'notifications, processed', description: 'Tables owned by the Notification service.' },
  { id: 'registry-db', label: 'Registry tables', sublabel: 'micro_lab_registry', description: 'Tables owned by the Registry service.' },
  { id: 'monolith', label: 'Monolith (comparison)', sublabel: 'one app, one transaction', description: 'The same workflow written as a single application. The only code that spans services\' tables.' },
  { id: 'monolith-db', label: 'Shared tables', sublabel: 'one transaction spans them', description: 'What a monolith can touch in a single transaction.' },
]

export const NODE_IDS = NODES.map((n) => n.id)

const DB_OF = (id) => (id.endsWith('-db') && id !== 'monolith-db' ? id : null)

const META = {
  client: { kind: 'client', glyph: 'client' },
  gateway: { kind: 'security', glyph: 'gateway', simulated: true },
  broker: { kind: 'mvc', glyph: 'broker', shape: 'broker', simulated: true },
  user: { kind: 'business', glyph: 'service' },
  product: { kind: 'business', glyph: 'service' },
  inventory: { kind: 'business', glyph: 'service' },
  order: { kind: 'business', glyph: 'service' },
  payment: { kind: 'business', glyph: 'service' },
  notification: { kind: 'business', glyph: 'service' },
  registry: { kind: 'mvc', glyph: 'registry', simulated: true },
  monolith: { kind: 'persistence', glyph: 'monolith' },
}
for (const n of NODES) {
  if (DB_OF(n.id) || n.id === 'monolith-db') META[n.id] = { kind: 'database', glyph: 'database', shape: 'database' }
}

/** Wide-layout position of each node: four columns (callers, platform, services, data) with wide gaps so line labels fit between nodes. */
const POSITIONS = {
  client: { x: 0, y: 396 },
  gateway: { x: 300, y: 132 },
  broker: { x: 300, y: 528 },
  user: { x: 600, y: 0 },
  product: { x: 600, y: 132 },
  inventory: { x: 600, y: 264 },
  order: { x: 600, y: 396 },
  payment: { x: 600, y: 528 },
  notification: { x: 600, y: 660 },
  registry: { x: 600, y: 792 },
  'user-db': { x: 900, y: 0 },
  'product-db': { x: 900, y: 132 },
  'inventory-db': { x: 900, y: 264 },
  'order-db': { x: 900, y: 396 },
  'payment-db': { x: 900, y: 528 },
  'notification-db': { x: 900, y: 660 },
  'registry-db': { x: 900, y: 792 },
  monolith: { x: 0, y: 792 },
  'monolith-db': { x: 300, y: 792 },
}

const LANES = [
  { id: 'callers', label: 'Callers', kind: 'client', nodes: ['client'] },
  { id: 'platform', label: 'Platform', caption: 'simulated', kind: 'mvc', nodes: ['gateway', 'broker'] },
  { id: 'services', label: 'Services', caption: 'each owns its data', kind: 'business', nodes: ['user', 'product', 'inventory', 'order', 'payment', 'notification', 'registry'] },
  { id: 'data', label: 'Data', caption: 'one MySQL schema', kind: 'database', nodes: ['user-db', 'product-db', 'inventory-db', 'order-db', 'payment-db', 'notification-db', 'registry-db'] },
  { id: 'comparison', label: 'Comparison', caption: 'monolith', kind: 'persistence', nodes: ['monolith', 'monolith-db'] },
]

const ROW_GAP = 132

/** Connection points from the geometry: horizontal when the nodes are further apart sideways, vertical otherwise. */
export function handlesFor(source, target) {
  const a = POSITIONS[source]
  const b = POSITIONS[target]
  const dx = b.x - a.x
  const dy = b.y - a.y
  // Two services in the same column that are not neighbours: go round the nodes in between instead of straight through them.
  if (dx === 0 && Math.abs(dy) > ROW_GAP) return ['l-s', 'l-t']
  if (Math.abs(dx) >= Math.abs(dy)) return dx >= 0 ? ['r-s', 'l-t'] : ['l-s', 'r-t']
  return dy >= 0 ? ['b-s', 't-t'] : ['t-s', 'b-t']
}

function layoutFor(edges) {
  const handles = {}
  const compactHandles = {}
  const edgeMeta = {}
  for (const e of edges) {
    handles[e.id] = handlesFor(e.source, e.target)
    compactHandles[e.id] = ['r-s', 'r-t']
    if (handles[e.id][0] === 'l-s' && handles[e.id][1] === 'l-t') edgeMeta[e.id] = { offset: 64 }
  }
  const nodeMeta = {}
  for (const id of NODE_IDS) nodeMeta[id] = META[id]
  return {
    wide: { positions: POSITIONS, handles },
    // Phones do not use the canvas (a list of calls is shown instead); the column is only the validated fallback.
    compact: { order: NODE_IDS, handles: compactHandles },
    heights: { wide: 940 },
    fullWidth: true,
    minWidth: 900,
    nodeMeta,
    edgeMeta,
    lanes: LANES,
  }
}

/** Every pair of nodes some step draws, plus the ownership line of every service whose database a step touches. */
function edgesOf(steps) {
  const ids = new Set()
  for (const s of steps) {
    for (const d of s.drawn ?? []) ids.add(d.id)
    if (s.edgeFrom && s.edgeTo) ids.add(`${s.edgeFrom}->${s.edgeTo}`)
  }
  return [...ids]
    .map((id) => {
      const [source, target] = id.split('->')
      return { id, source, target }
    })
    .filter((e) => POSITIONS[e.source] && POSITIONS[e.target])
}

function buildScenario({ id, title, mode, steps }) {
  const edges = edgesOf(steps)
  return {
    id,
    title,
    mode,
    nodes: NODES,
    edges,
    steps,
    layout: layoutFor(edges),
    edgeOverlay: (index) => edgeOverlay(steps, index),
    nodeOverlay: (index) => nodeOverlay(steps, index),
  }
}

/** The LIVE scenario of one finished run: the server's own steps and calls on the diagram. */
export function liveScenario(runResult, info) {
  const steps = enrichSteps(runResult.steps, runResult.calls)
  return buildScenario({ id: `micro-live-${info.id}`, title: info.title, mode: 'LIVE', steps })
}

// ---------------------------------------------------------------------------------------------------------------- SIMULATED walkthroughs

const line = (from, to, mechanism) => ({ id: `${from}->${to}`, mechanism, failed: false, outcome: null, labels: [mechanism] })

const sim = (id, title, nodes, edgeFrom, edgeTo, explanation, drawn) => ({
  id, title, nodes, edgeFrom, edgeTo, direction: 'REQUEST', provenance: 'CONCEPTUAL', explanation, evidence: [], sql: [], codeRefId: null, drawn,
})

const WALKTHROUGHS = {
  monolith: [
    sim('one-transaction', 'A monolith: one application, one transaction', ['client', 'monolith', 'monolith-db'], 'client', 'monolith',
      'All the order logic runs in one application and touches users, products, stock and orders in ONE database transaction, so the whole workflow commits or rolls back together.',
      [line('client', 'monolith', 'SYNC'), line('monolith', 'monolith-db', 'LOCAL_TX')]),
    sim('split', 'Microservices: the same workflow, split by business capability', ['client', 'order', 'user', 'product', 'inventory'], 'client', 'order',
      'The Order service calls the User, Product and Inventory services. Each one owns its own tables, can be changed and deployed on its own, and can fail on its own.',
      [line('client', 'order', 'SYNC'), line('order', 'user', 'SYNC'), line('order', 'product', 'SYNC'), line('order', 'inventory', 'SYNC')]),
    sim('own-transactions', 'Each service commits its own local transaction', ['order', 'order-db', 'inventory', 'inventory-db'], 'order', 'order-db',
      'There is no single transaction any more. The order row and the reservation commit separately, so a failure between them leaves the system partly done.',
      [line('order', 'order-db', 'LOCAL_TX'), line('inventory', 'inventory-db', 'LOCAL_TX')]),
    sim('modular-monolith', 'The middle road: a modular monolith', ['monolith', 'monolith-db'], 'monolith', 'monolith-db',
      'One deployable with enforced module boundaries keeps the single transaction and the simple operations, and is often the better choice until independent deployment or scaling is really needed.',
      [line('monolith', 'monolith-db', 'LOCAL_TX')]),
  ],
  communication: [
    sim('sync', 'A synchronous call: the caller waits', ['client', 'order', 'inventory'], 'order', 'inventory',
      'The Order service sends a request and waits for the answer. Both services must be up at the same moment, and the caller\'s time includes the callee\'s.',
      [line('client', 'order', 'SYNC'), line('order', 'inventory', 'SYNC')]),
    sim('outcomes', 'A call can end in six different ways', ['order', 'inventory'], 'order', 'inventory',
      'Success, a business refusal, unreachable, a timeout, an unexpected error, or a local refusal. After a timeout the caller cannot know whether the work was done.',
      [line('order', 'inventory', 'SYNC')]),
    sim('event', 'An event: the sender does not wait', ['order', 'broker', 'notification'], 'order', 'broker',
      'The Order service publishes OrderPlaced and carries on. The consumer handles it later, in its own request. This lab\'s channel is a SIMULATED in-memory list, not Kafka or RabbitMQ.',
      [line('order', 'broker', 'EVENT'), line('broker', 'notification', 'EVENT')]),
    sim('duplicates', 'Events can arrive twice', ['broker', 'notification', 'notification-db'], 'broker', 'notification',
      'At-least-once delivery means a consumer can see the same event again. A consumer that records processed message ids in the same local transaction as its effect ignores the duplicate.',
      [line('broker', 'notification', 'EVENT'), line('notification', 'notification-db', 'LOCAL_TX')]),
  ],
  gateway: [
    sim('entry', 'One entry point', ['client', 'gateway'], 'client', 'gateway',
      'Clients talk to the gateway, not to the services. It hides the internal structure and applies the same rules to every request.',
      [line('client', 'gateway', 'SYNC')]),
    sim('guards', 'Authenticate and limit before routing', ['gateway'], null, null,
      'The gateway rejects a request without a valid credential (401) and one over its rate limit (429) before any service sees it.', []),
    sim('route', 'Route to the owning service', ['gateway', 'order', 'product', 'user'], 'gateway', 'order',
      'The gateway looks up the route and forwards the call. Internal services have no route, so they cannot be reached from outside.',
      [line('gateway', 'order', 'SYNC'), line('gateway', 'product', 'SYNC'), line('gateway', 'user', 'SYNC')]),
    sim('translate', 'Translate failures', ['gateway', 'product'], 'gateway', 'product',
      'A service that is down, slow or broken becomes 503, 504 or 502 for the client. If the gateway itself is down, every service is unreachable for clients.',
      [line('gateway', 'product', 'SYNC')]),
  ],
  discovery: [
    sim('register', 'Instances register', ['inventory', 'registry', 'registry-db'], 'inventory', 'registry',
      'Each instance tells the registry its name, id and address, and keeps sending heartbeats. This lab\'s registry is a table plus code, not Eureka.',
      [line('inventory', 'registry', 'SYNC'), line('registry', 'registry-db', 'LOCAL_TX')]),
    sim('lookup', 'Clients look a service up by name', ['client', 'registry'], 'client', 'registry',
      'Instead of a fixed address the client asks for a healthy instance of a name, and gets one in turn (round-robin).',
      [line('client', 'registry', 'SYNC')]),
    sim('health', 'Health decides who is returned', ['registry', 'inventory'], 'registry', 'inventory',
      'An instance is healthy while its last report was UP and its heartbeat is at most 3000 simulated ms old. Exactly 3000 ms is still healthy; 3001 ms is stale.', []),
    sim('stale', 'The registry only knows what it is told', ['client', 'registry', 'inventory'], 'client', 'inventory',
      'An instance that crashes silently stays listed as healthy until its heartbeat expires, and clients that pick it meanwhile fail.',
      [line('client', 'inventory', 'SYNC')]),
  ],
  'database-per-service': [
    sim('own', 'Each service owns its tables', ['order', 'order-db', 'inventory', 'inventory-db'], 'order', 'order-db',
      'Only the owning service reads or writes its tables. Everything else it needs, it asks for through a contract.',
      [line('order', 'order-db', 'LOCAL_TX'), line('inventory', 'inventory-db', 'LOCAL_TX')]),
    sim('contract', 'Data is shared by asking, not by joining', ['order', 'inventory'], 'order', 'inventory',
      'The Order service asks Inventory for the stock level. It never queries the stock table, so Inventory can change its schema freely.',
      [line('order', 'inventory', 'SYNC')]),
    sim('no-fk', 'Foreign keys stop at the boundary', ['order', 'product'], 'order', 'product',
      'An order refers to a product by id. Nothing in MySQL stops the Product service deleting it, so references can dangle and copied data goes stale.',
      [line('order', 'product', 'SYNC')]),
    sim('logical', 'Logical is not physical isolation', ['order-db', 'inventory-db'], null, null,
      'Here the separate tables share one MySQL schema, so the separation is enforced by lab code. Separate databases would be enforced by the database servers.', []),
  ],
  saga: [
    sim('no-global', 'No transaction spans services', ['order', 'inventory', 'payment'], 'order', 'inventory',
      'Reserve stock, charge the payment, confirm the order: each is a separate local transaction in a different service.',
      [line('order', 'inventory', 'SYNC'), line('order', 'payment', 'SYNC')]),
    sim('compensate', 'A failed step is undone by compensation', ['order', 'inventory'], 'order', 'inventory',
      'If the payment fails, the Order service asks Inventory to release the stock. That is a new business operation, not a database rollback: the reservation stays as history.',
      [line('order', 'inventory', 'COMPENSATION')]),
    sim('choreography', 'Choreography: services react to events', ['broker', 'inventory', 'payment', 'order'], 'broker', 'payment',
      'No coordinator: each service does its step when an event arrives and publishes the next one. State is spread over the services.',
      [line('broker', 'inventory', 'EVENT'), line('broker', 'payment', 'EVENT'), line('broker', 'order', 'EVENT')]),
    sim('outbox', 'The outbox: write the event with the data', ['order', 'order-db', 'broker'], 'order', 'order-db',
      'The order and an outbox row are written in ONE local transaction; a relay publishes the row later. Delivery is at-least-once, never exactly-once.',
      [line('order', 'order-db', 'LOCAL_TX'), line('order', 'broker', 'OUTBOX')]),
  ],
  resilience: [
    sim('retry', 'Retry with a bound and a backoff', ['client', 'inventory'], 'client', 'inventory',
      'A failed call is tried again a limited number of times, waiting longer each time. Business refusals are never retried; timeouts only when repeating is safe.',
      [line('client', 'inventory', 'SYNC')]),
    sim('breaker', 'The circuit breaker stops the hammering', ['client', 'product'], 'client', 'product',
      'After repeated failures the breaker opens and calls are refused locally, so the failing service gets time to recover. A probe call closes it again.',
      [line('client', 'product', 'REFUSED')]),
    sim('idempotency', 'Idempotency keys make retries safe', ['client', 'payment', 'payment-db'], 'client', 'payment',
      'The client sends the same key on every retry. The Payment service finds the first charge and returns it instead of charging again.',
      [line('client', 'payment', 'SYNC'), line('payment', 'payment-db', 'LOCAL_TX')]),
    sim('bulkhead', 'Bulkheads contain the damage', ['client', 'payment', 'inventory'], 'client', 'payment',
      'Each service has its own concurrency permits, so a slow Payment service cannot use up the capacity Inventory needs.',
      [line('client', 'payment', 'REFUSED'), line('client', 'inventory', 'SYNC')]),
  ],
  observability: [
    sim('spans', 'Every call is a span', ['client', 'order', 'user', 'product', 'inventory'], 'client', 'order',
      'A span records the service, the operation, the status and the duration. Child spans point at their parent, all under one correlation id.',
      [line('client', 'order', 'SYNC'), line('order', 'user', 'SYNC'), line('order', 'product', 'SYNC'), line('order', 'inventory', 'SYNC')]),
    sim('attribution', 'Self time says who spent the time', ['order', 'product'], 'order', 'product',
      'A span\'s self time is its duration minus its children. It blames the service that was slow, not the one that waited.',
      [line('order', 'product', 'SYNC')]),
    sim('propagation', 'The correlation id must be passed on', ['order', 'broker', 'notification'], 'order', 'broker',
      'A service that forgets to pass the id on, including inside an event, breaks the trace: the work downstream cannot be tied back to the request.',
      [line('order', 'broker', 'EVENT'), line('broker', 'notification', 'EVENT')]),
    sim('limits', 'What this lab does not simulate', ['client', 'order'], null, null,
      'Real tracing collects spans from many processes over a network (for example with OpenTelemetry). Here one process generates all spans, so only the concepts are shown.', []),
  ],
}

/** A conceptual walkthrough for a topic. Every step is SIMULATED (CONCEPTUAL): nothing in it was observed. */
export function simulatedScenario(topicId) {
  const steps = WALKTHROUGHS[topicId]
  const topic = TOPICS.find((t) => t.id === topicId)
  return buildScenario({ id: `micro-sim-${topicId}`, title: `${topic.title} (conceptual walkthrough)`, mode: 'SIMULATED', steps })
}

// ---------------------------------------------------------------------------------------------------------------- the eight topics

const lvl = (beginner, intermediate, advanced) => ({ beginner, intermediate, advanced })

export const TOPICS = [
  {
    id: 'monolith',
    title: 'Monolith vs microservices',
    explain: {
      what: 'A monolith is one deployable application whose parts share one process, one database and one transaction. Microservices split an application by business capability into services that are deployed on their own and each own their data. A modular monolith keeps one deployable but enforces module boundaries inside it.',
      why: 'Splitting buys independent deployment, scaling and team ownership. It costs the single transaction, adds network calls that can fail, and multiplies what must be operated. The right choice depends on the problem, not on fashion.',
      who: [
        { name: 'The monolith (comparison)', role: 'runs the whole order workflow in one local transaction across all the tables' },
        { name: 'Order, User, Product, Inventory, Notification', role: 'the same workflow as cooperating services, each with its own tables' },
        { name: 'The service bus', role: 'carries every call between services and records it' },
        { name: 'MySQL', role: 'one schema here; one transaction in the monolith, many separate local transactions across services' },
      ],
      where: 'At the top-level structure of an application: how code is cut into deployable units, and where the data lives.',
      when: 'At design time (how to cut the system), and again whenever a boundary hurts: a change that always touches two services, or a team that is blocked on another.',
      how: [
        'Run the same order workflow in one transaction and as a set of services.',
        'Inject a failure after the stock is reserved and compare what is left behind.',
        'Take a hard dependency down and see which orders can still be placed.',
        'Count which tables each service touched and estimate the cost of splitting every step.',
        'Decide where a boundary pays for itself.',
      ],
    },
    levels: lvl(
      'A monolith is one program doing everything. Microservices are many small programs that work together. More programs means more things that can break, so you only split when it is worth it.',
      'The deciding factors are independent deployment and scaling, team ownership, and data ownership. The cost is distributed failure: partial success, timeouts, and the loss of a single transaction across the workflow.',
      'Treat service boundaries as business capabilities with their own data and rate of change. A modular monolith gives most of the structure without the distribution cost; extract a service only when a boundary needs independent scaling, release or fault isolation.',
    ),
    mistakes: [
      'Splitting by technical layer or class instead of by business capability, which creates chatty services that always change together.',
      'Keeping one shared database and calling it microservices: the services are still coupled through the schema.',
      'Assuming a failed multi-service workflow leaves the system consistent. Without a saga it usually does not.',
    ],
    observations: [
      'The monolith\'s failed workflow leaves nothing behind; the services\' failed workflow leaves stock reserved with no placed order.',
      'A synchronous dependency makes the caller exactly as available as that dependency.',
      'Splitting every step multiplies hops and the number of services that must all be up.',
    ],
  },
  {
    id: 'communication',
    title: 'Service communication',
    explain: {
      what: 'Services talk either synchronously, where the caller waits for the answer (request/response), or asynchronously, where a sender publishes an event and a consumer handles it later. A call can end as success, a business refusal, unavailable, a timeout, an unexpected error, or a local refusal; these mean different things.',
      why: 'Synchronous calls are simple and give an immediate answer, but couple the services in time. Events decouple them, at the price of eventual consistency and consumers that must cope with delay and duplicates.',
      who: [
        { name: 'The Order service', role: 'calls User, Product and Inventory synchronously and announces orders with events' },
        { name: 'The service bus', role: 'applies injected faults and timeouts and records every call' },
        { name: 'The event channel (simulated)', role: 'an in-memory list that holds events until a consumer is delivered to' },
        { name: 'The Notification service', role: 'consumes OrderPlaced, with or without remembering message ids' },
      ],
      where: 'On every link between services, and between a service and the messages it produces or consumes.',
      when: 'At every interaction. Timeouts and retries matter most when a dependency is slow or failing.',
      how: [
        'Place an order through three synchronous calls and add up their time.',
        'Make a dependency fail in different ways and read what the caller can conclude.',
        'Publish an event, then deliver it to the consumer as a separate step.',
        'Deliver the same event again to a naive and to an idempotent consumer.',
        'Compare synchronous and asynchronous notification, healthy and failing.',
      ],
    },
    levels: lvl(
      'A call is like a phone call: you wait for an answer. An event is like leaving a note: you carry on, and someone reads it later.',
      'After a timeout you cannot tell whether the work was done. That is why retries need idempotent operations, and why consumers of events must tolerate duplicates.',
      'Choose per interaction: synchronous when the caller needs the result to continue, asynchronous when others merely react. Keep the dependency graph acyclic, bound every wait, and make every handler idempotent.',
    ),
    mistakes: [
      'Treating every failure the same: a business refusal, an outage and a timeout call for different reactions.',
      'Retrying a timed-out operation that is not idempotent, which repeats its side effects.',
      'Assuming an event is delivered exactly once. Channels deliver at least once.',
    ],
    observations: [
      'A timeout can hide completed work: the stock was reserved although the caller saw TIMEOUT.',
      'Synchronous notification reports a failure for an order that WAS placed.',
      'A naive consumer sends a second notification for a duplicate event; an idempotent one does not.',
    ],
  },
  {
    id: 'gateway',
    title: 'API gateway',
    explain: {
      what: 'An API gateway is the single entry point for clients. It authenticates requests, limits their rate, routes them to the owning service and translates failures into responses. The gateway in this lab is a small in-process model of those jobs, not a production gateway.',
      why: 'It keeps the internal structure private, applies cross-cutting rules once, and gives clients one stable address. It is not service discovery: discovery says where a service is, the gateway says which service a request is for.',
      who: [
        { name: 'The gateway (simulated)', role: 'checks the credential state, counts requests per client, finds the route and forwards the call' },
        { name: 'The route table', role: 'public paths only; internal services such as Inventory have no route' },
        { name: 'The services behind it', role: 'answer the forwarded call; a failure becomes 502, 503 or 504' },
        { name: 'The simulated clock', role: 'defines the rate-limit window, so a run is repeatable' },
      ],
      where: 'At the edge of the system, between clients and services.',
      when: 'On every external request, before any service is called.',
      how: [
        'Route three public paths to three different services.',
        'Ask for an internal path and for an unknown path and see the gateway answer 404 itself.',
        'Send a missing and an expired credential and check that no service is called.',
        'Burst past the rate limit, then wait for the next window.',
        'Fail a service, then the gateway itself, and compare the client\'s view.',
      ],
    },
    levels: lvl(
      'The gateway is the front door. Visitors are checked at the door and sent to the right room, and rooms that are private have no door.',
      'The gateway authenticates, limits, routes and translates errors. Services behind it still authorise what each caller may do, and must not be reachable around it.',
      'A gateway is a single point of failure unless replicated, and a place where policy and latency accumulate. Keep it thin: routing and cross-cutting policy, not business logic.',
    ),
    mistakes: [
      'Relying on the gateway as the only security check.',
      'Putting business logic in the gateway, which makes it the new monolith.',
      'Exposing every internal service through the gateway.',
    ],
    observations: [
      'Rejected requests reach no service at all.',
      'The rate limit is per client and resets with the window.',
      'With the gateway down, healthy services are unreachable for clients.',
    ],
  },
  {
    id: 'discovery',
    title: 'Service discovery',
    explain: {
      what: 'Service discovery lets a client find a healthy instance of a service by name instead of a fixed address. Instances register, send heartbeats and report health; clients ask the registry for an instance and may be given them in turn (load balancing). The registry in this lab is a table plus code, not Eureka.',
      why: 'Instances come and go as services scale and restart. A fixed address breaks; a lookup by name does not. But the registry only knows what instances tell it.',
      who: [
        { name: 'The registry (simulated)', role: 'stores instances, ages their heartbeats and answers lookups round-robin' },
        { name: 'Service instances', role: 'register, heartbeat and report health' },
        { name: 'The client', role: 'resolves a name to an instance and calls it' },
        { name: 'The expiry rule', role: 'healthy while the heartbeat is at most 3000 simulated ms old; stale from 3001 ms' },
      ],
      where: 'Between callers and the services they call, usually in the client or the platform.',
      when: 'At start-up (registration), continuously (heartbeats), and on every call that resolves a name.',
      how: [
        'Register instances and look them up; ask for a service nobody registered.',
        'Spread requests over several instances.',
        'Report an instance DOWN and watch it get skipped.',
        'Crash an instance silently and see it recommended until its heartbeat expires.',
      ],
    },
    levels: lvl(
      'It is a phone book for services: you look up a name and get a number that currently works.',
      'Health comes from heartbeats and health checks. A short expiry detects crashes quickly but may drop healthy instances whose heartbeat is late.',
      'Expect stale entries: combine discovery with client retries to another instance, circuit breakers and timeouts. Round-robin ignores load; real balancers weigh it.',
    ),
    mistakes: [
      'Trusting the registry as if it always knew the real state of every instance.',
      'Setting the expiry so short that a slow heartbeat removes healthy instances.',
      'Treating "no healthy instance" as a connection error instead of a case the caller must handle.',
    ],
    observations: [
      'Requests are shared evenly by round-robin.',
      'A silently crashed instance is still returned until the heartbeat expires.',
      'The expiry rule is exact: 3000 ms is healthy, 3001 ms is stale.',
    ],
  },
  {
    id: 'database-per-service',
    title: 'Database per service',
    explain: {
      what: 'Each service owns its data and is the only code that reads or writes it. Other services get data by asking through the owner\'s contract. Here the services\' tables share one MySQL schema, so the isolation is logical (enforced by lab code), not physical (separate database servers).',
      why: 'Ownership lets a service change its schema, storage and scaling without coordinating with its readers. The price is duplication, references that can dangle, and no joins or foreign keys across services.',
      who: [
        { name: 'Each service\'s repository', role: 'the only code that touches that service\'s tables' },
        { name: 'The ownership guard', role: 'refuses, at run time, a statement on a table the service does not own' },
        { name: 'The contract', role: 'the only way another service gets the data' },
        { name: 'MySQL', role: 'enforces foreign keys inside a service, and nothing across services' },
      ],
      where: 'Between each service and its persistence, and at every place another service wants that data.',
      when: 'At design time (who owns what), and every time someone is tempted to read another service\'s table.',
      how: [
        'Let Order write orders and Inventory change stock on its own.',
        'Try a direct read of another service\'s table and see it refused.',
        'Delete a product that an order refers to and see the reference dangle.',
        'Break a direct reader by changing the owner\'s schema, and see the contract survive.',
      ],
    },
    levels: lvl(
      'Each service keeps its own notebook. If you want to know something from another notebook, you ask its owner instead of reading it yourself.',
      'Foreign keys and joins stop at the boundary, so services refer to each other by id, copy what they need on purpose, and treat NOT_FOUND as normal.',
      'A shared schema makes the table layout a hidden public API. Own your data, expose capabilities, and keep copies fresh with events only where freshness matters.',
    ),
    mistakes: [
      'Reading another service\'s tables "just this once".',
      'Adding a cross-service foreign key, which forces both tables into one database and one release.',
      'Forgetting that copied data goes stale.',
    ],
    observations: [
      'A statement on another service\'s table is refused before MySQL sees it.',
      'MySQL enforces a foreign key inside Inventory (error 1451) but not between Order and Product.',
      'A direct reader fails with a real MySQL error when the schema changes; the contract keeps working.',
    ],
  },
  {
    id: 'saga',
    title: 'Distributed transactions and sagas',
    explain: {
      what: 'A local transaction covers one service\'s database. A workflow across services has no such transaction, so a saga performs it as a sequence of local transactions and, if a step fails, runs compensating operations for the steps already done. Orchestration coordinates the steps from one place; choreography lets services react to each other\'s events. The transactional outbox makes "save the data and publish the event" reliable.',
      why: 'Two-phase commit across services needs every participant to support it and blocks on failure. Sagas keep services independent and available, and give up isolation: other readers can see the intermediate state.',
      who: [
        { name: 'The saga orchestrator (in the Order service)', role: 'runs reserve, charge and confirm, logs each step and compensates in reverse order' },
        { name: 'Inventory and Payment', role: 'do their step in a local transaction and offer a compensation (release, refund)' },
        { name: 'The outbox and its relay', role: 'write the event with the data and publish it later, at least once' },
        { name: 'The event channel (simulated)', role: 'carries the events of a choreography and of the relay' },
      ],
      where: 'Across any workflow that changes data in more than one service.',
      when: 'Whenever a step fails after earlier steps committed, and whenever an event must follow a data change reliably.',
      how: [
        'Run a successful order across Inventory and Payment.',
        'Make the payment fail after the reservation and watch the release compensate.',
        'Make the release fail and see the saga wait in COMPENSATION_FAILED, then resume it.',
        'Run the same workflow as a choreography, step by step.',
        'Write an order and its outbox row together, roll both back together, and relay later.',
      ],
    },
    levels: lvl(
      'If one step of a multi-step job fails, you cannot "undo" the earlier steps with a button. You do a new job that cancels them, like refunding a payment.',
      'Compensation is a business operation, not a rollback. The outbox stores the event with the data so a crash cannot lose it, and consumers must handle duplicates.',
      'Choose orchestration for visibility and control, choreography for decoupling. Persist saga state, make every step and compensation idempotent, and accept that delivery is at-least-once: exactly-once effects come from idempotent consumers.',
    ),
    mistakes: [
      'Describing a compensation as a rollback. The effect was committed and visible; the compensation is a new operation.',
      'Publishing an event after the commit without an outbox: a crash in between loses it.',
      'Assuming the outbox gives exactly-once delivery. It gives at-least-once.',
    ],
    observations: [
      'A rolled-back local transaction leaves no rows; a compensated saga leaves a RELEASED reservation as history.',
      'Between two local transactions the intermediate state is visible to other readers.',
      'A relay crash republishes the same message id; an idempotent consumer acts once.',
    ],
  },
  {
    id: 'resilience',
    title: 'Resilience and fault tolerance',
    explain: {
      what: 'Resilience patterns limit the damage of failures: timeouts bound a wait, retries with backoff overcome transient faults, a circuit breaker stops calling a failing service, a fallback gives a degraded answer, a bulkhead isolates capacity, and idempotency keys make retries safe. The implementations here are SpringLab\'s own deterministic code on a simulated clock, not a resilience library.',
      why: 'In a distributed system something is always failing. Without limits, one slow service exhausts everyone\'s threads, and uncontrolled retries multiply the load on the service that can least afford it.',
      who: [
        { name: 'The resilient caller', role: 'retries with bounded attempts and deterministic backoff, consults a breaker and applies a fallback' },
        { name: 'The circuit breaker', role: 'CLOSED, OPEN and HALF_OPEN, driven by the simulated clock' },
        { name: 'The bulkhead', role: 'real Semaphore permits per service' },
        { name: 'The Payment service', role: 'honours idempotency keys so a retried charge is not repeated' },
      ],
      where: 'In the caller, around every remote call, and in the services that must tolerate repeated requests.',
      when: 'When a call fails, is slow, or is repeated; and continuously for the breaker and the permits.',
      how: [
        'Retry a transient failure with growing waits, up to a limit.',
        'Open the breaker with repeated failures, serve a fallback, probe, close it, and open it again.',
        'Retry a timed-out charge with and without an idempotency key.',
        'Retry at two layers and count the calls the failing service receives.',
        'Saturate Payment and see whether other services are affected.',
      ],
    },
    levels: lvl(
      'If a shop is closed, you do not knock a hundred times. You wait, try a few times, and then stop and go somewhere else.',
      'Retry only what is safe, bound it, and back off. Open the breaker on repeated technical failures, not on business refusals. Isolate capacity per dependency.',
      'Retries at several layers multiply. Budget them, retry at one layer, use jitter in production, and make operations idempotent. A fallback must be an honest degradation the business accepts.',
    ),
    mistakes: [
      'Retrying immediately and without a limit.',
      'Retrying a timed-out non-idempotent operation, such as a charge, without a key.',
      'Sharing one pool of threads across all dependencies.',
    ],
    observations: [
      'Waits are 100, 200, 400 ms (capped at 800): the same on every run.',
      'While OPEN, no call reaches the failing service.',
      'Retries at two layers send the product of the attempts (3 x 3 = 9) to the failing service.',
    ],
  },
  {
    id: 'observability',
    title: 'Observability and distributed tracing',
    explain: {
      what: 'Observability is being able to ask what a system did. Distributed tracing records each call as a span (service, operation, status, duration) linked to its parent, all under one correlation id. Metrics and logs complement it. In this lab tracing is an in-process simulation, not OpenTelemetry.',
      why: 'A single request crosses many services. Without correlated spans you cannot tell which service was slow, where a failure started, or which work belongs to which request.',
      who: [
        { name: 'The span', role: 'one call: service, operation, status, parent, correlation id and duration' },
        { name: 'The correlation id', role: 'ties every span of one request together' },
        { name: 'The service bus', role: 'creates a span for every call and passes the correlation id on (or does not)' },
        { name: 'The clock', role: 'simulated milliseconds for durations; the real elapsed microseconds are recorded separately' },
      ],
      where: 'Across every hop of a request, including hops through events.',
      when: 'For every request; the value shows when something is slow or failing.',
      how: [
        'Trace one order and read the span tree.',
        'Compute self time to see which service spent the time.',
        'Fail a dependency and walk back from the root to the first failing span.',
        'Stop passing the correlation id and watch the trace break.',
        'Send the correlation id inside an event and see the consumer join the trace.',
      ],
    },
    levels: lvl(
      'A trace is a receipt for one request: it lists every service that helped, in order, with how long each took.',
      'Each span has a parent. Self time (duration minus children) blames the service that was slow. Failures change form as they travel up, so follow the parent links to the cause.',
      'Propagation is a convention every hop must follow, including queues and background jobs. Sampling, collectors and span links for asynchronous work are what real systems add; they are not simulated here.',
    ),
    mistakes: [
      'Not passing the trace context through message queues.',
      'Blaming the service that reported the error instead of the one that caused it.',
      'Logging credentials or request bodies into spans.',
    ],
    observations: [
      'The slowest service is the one with the largest self time, not the root.',
      'A failure starts at a leaf span and is reported upward as a different status.',
      'Without propagation each downstream span has a new correlation id and no parent.',
    ],
  },
]

// ---------------------------------------------------------------------------------------------------------------- explanations and takeaways

export const SIMULATED_NOTES = [
  { id: 'network', title: 'The network and its partitions', body: 'Calls here are method calls. Real networks drop, delay, duplicate and reorder messages and can split into partitions where both sides keep running. Injected faults imitate some outcomes; partitions are explained, not run.' },
  { id: 'brokers', title: 'Kafka, RabbitMQ and real delivery guarantees', body: 'The event channel is an in-memory list. A real broker stores messages durably, partitions them, tracks consumer offsets and can be down itself. None of that is run here.' },
  { id: 'two-phase-commit', title: 'Two-phase commit', body: 'Two-phase commit would make several services commit as one by asking every participant to prepare and then commit. It is not run: it needs every participant to support it, holds locks across the network, and blocks if the coordinator fails.' },
  { id: 'platform', title: 'Kubernetes, scaling and independent deployment', body: 'All services run in one JVM, so nothing is deployed, scaled, restarted or isolated independently, and one crash would take everything down. Those benefits (and their cost) are explained, not demonstrated.' },
  { id: 'tracing', title: 'OpenTelemetry, collectors and sampling', body: 'Spans and correlation ids are generated and passed by lab code in one process. Real tracing propagates headers across the network, collects spans from many processes and usually samples them.' },
  { id: 'physical-isolation', title: 'Physically separate databases', body: 'Every table lives in one MySQL schema. Ownership is enforced by lab code and a test, not by database servers or credentials, which is what real separation would use.' },
]

export const STATIC_ASSUMPTIONS = [
  'Everything runs inside the SpringLab application and its local MySQL database. There is no network, no message broker and no separate deployment.',
  'Every run uses its own temporary rows in the micro_lab_* tables, created and removed by that run (the count removed is reported). No SpringLab application table is read or changed.',
  'Time is simulated: durations, timeouts, backoff waits and the registry TTL use a logical clock. Measured microseconds in the span data are the real elapsed time of in-process calls.',
  'LIVE steps were observed while the run executed (calls, SQL, rows, state). INFERRED steps are calculated from those observations. SIMULATED steps explain a concept and observed nothing.',
  'The event channel, the gateway and the registry are small models. They are not Kafka or RabbitMQ, not a production gateway and not Eureka.',
  'The circuit breaker, retry and idempotency code are SpringLab\'s own deterministic state machines, not a resilience library.',
  'Percentages such as availability in the monolith experiments are labelled illustrative arithmetic, not measurements. The lab makes no performance claim.',
]

/** Key points for the Explain stage, each linked to the experiments that show it. Every id exists in the catalogue (checked by a test). */
export const TAKEAWAYS = [
  { text: 'A monolith can make a workflow all-or-nothing with one transaction. Across services each step commits on its own, so a failure midway leaves partial work.', experiments: ['mono-order-workflow', 'saga-compensation-vs-rollback'] },
  { text: 'A synchronous dependency decides whether you can serve a request at all. A dependency informed by an event can be down without stopping the order.', experiments: ['mono-coupling', 'comm-sync-vs-async'] },
  { text: 'A call can end six different ways. After a timeout the caller cannot know whether the work was done.', experiments: ['comm-failure-semantics', 'comm-sync-failure'] },
  { text: 'Events are delivered at least once. An idempotent consumer, recording the message id with its effect, makes duplicates harmless.', experiments: ['comm-async-duplicate', 'outbox-duplicate-delivery'] },
  { text: 'A gateway authenticates, limits and routes before any service is called, and it is a single point of failure unless replicated.', experiments: ['gw-routing', 'gw-auth', 'gw-rate-limit', 'gw-failure'] },
  { text: 'A registry only knows what instances tell it. A silently crashed instance is recommended until its heartbeat expires.', experiments: ['disc-stale-registration', 'disc-unavailable', 'disc-load-balancing'] },
  { text: 'Each service owns its tables. Foreign keys and joins stop at the boundary, and a shared table turns the schema into a hidden API.', experiments: ['dbs-forbidden-access', 'dbs-foreign-keys', 'dbs-shared-coupling'] },
  { text: 'Committing the data and then publishing the event are two writes. The transactional outbox writes the event with the data, and a relay publishes it later.', experiments: ['outbox-dual-write-gap', 'outbox-commit-rollback'] },
  { text: 'A saga undoes a failed workflow with compensating operations, in reverse order. A compensation is a new business operation, and it can fail.', experiments: ['saga-orchestrated', 'saga-choreographed'] },
  { text: 'Retries must be bounded and backed off, only for what is safe, and idempotency keys are what make a retried charge harmless.', experiments: ['res-retry-backoff', 'res-idempotency-key'] },
  { text: 'A circuit breaker stops calls to a failing service and probes for recovery; retries at several layers multiply the load.', experiments: ['res-circuit-breaker', 'res-retry-amplification'] },
  { text: 'A bulkhead gives each dependency its own capacity, so one slow service cannot starve the others.', experiments: ['res-bulkhead'] },
  { text: 'A trace links every call of a request by parent and correlation id. Self time blames the service that was slow; a lost correlation id breaks the trace.', experiments: ['obs-trace-order', 'obs-failure-propagation', 'obs-missing-correlation', 'obs-async-correlation'] },
]

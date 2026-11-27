/**
 * The learning modules the dashboard offers. `status` is the only availability signal: 'available' modules have a route,
 * 'upcoming' ones are shown as such and link nowhere. Descriptions repeat what each lesson actually teaches.
 */
export const MODULES = [
  {
    id: 'rest-lifecycle',
    title: 'REST Request Lifecycle',
    description: 'Follow one request through Spring Security, Spring MVC, the service layer and Hibernate.',
    icon: 'route',
    path: '/learn/rest-lifecycle',
    status: 'available',
    topicSlug: 'rest-lifecycle',
  },
  {
    id: 'jwt-security',
    title: 'JWT Security Simulator',
    description: 'Take a token apart and watch SpringLab answer 401 or 403 and say why.',
    icon: 'shield',
    path: '/learn/jwt-security',
    status: 'available',
    topicSlug: 'jwt-security',
  },
  {
    id: 'jpa-relationships',
    title: 'JPA Relationship Lab',
    description: 'See how entity relationships become tables and foreign keys, which side writes them, and what SQL Hibernate prepares.',
    icon: 'database',
    path: '/learn/jpa-relationships',
    status: 'available',
    topicSlug: 'jpa-hibernate',
  },
  {
    id: 'jdbc',
    title: 'JDBC Lab',
    description: 'Run real JDBC, Spring JDBC and Hibernate against MySQL: statements, transactions on two connections, connection pools.',
    icon: 'database',
    path: '/learn/jdbc',
    status: 'available',
    topicSlug: 'jdbc',
  },
  {
    id: 'microservices',
    title: 'Microservices Architecture Lab',
    description: 'Run real service workflows against MySQL: calls, events, local transactions, outbox, sagas, resilience and tracing, and how they fail.',
    icon: 'database',
    path: '/learn/microservices',
    status: 'available',
    topicSlug: 'microservices',
  },
]

export function moduleForPath(pathname) {
  return MODULES.find((m) => m.path && pathname.startsWith(m.path))
}

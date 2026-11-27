import client from '../../api/client'

/** Experiment groups and experiments, with the question each asks and the outcome the lesson claims. */
export async function getJpaCatalog() {
  return (await client.get('/api/lab/jpa/catalog')).data
}

/** The object model and relational schema, read by the server from the real annotations and from MySQL. */
export async function getJpaMapping() {
  return (await client.get('/api/lab/jpa/mapping')).data
}

/**
 * Runs ONE predefined experiment on the server. The only input is the experiment id: there is no body, so nothing a
 * learner types (or tampers with) can change what runs.
 */
export async function runJpaExperiment(id) {
  return (await client.post(`/api/lab/jpa/experiments/${encodeURIComponent(id)}/run`)).data
}

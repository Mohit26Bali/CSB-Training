import client from '../../api/client'

/** Experiment groups, experiments (question, claim, diagram), diagrams and limitations. */
export async function getJdbcCatalog() {
  return (await client.get('/api/lab/jdbc/catalog')).data
}

/** The lab tables read from MySQL's information_schema (no connection URL or credentials). */
export async function getJdbcMapping() {
  return (await client.get('/api/lab/jdbc/mapping')).data
}

/** Runs ONE predefined experiment. The only input is the id: no body, so nothing a learner types can change what runs. */
export async function runJdbcExperiment(id) {
  return (await client.post(`/api/lab/jdbc/experiments/${encodeURIComponent(id)}/run`)).data
}

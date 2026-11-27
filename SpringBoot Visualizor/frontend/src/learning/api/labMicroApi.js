import client from '../../api/client'

const BASE = '/api/lab/microservices'

/** The eight simulated services (responsibility, owned tables), the limits and the limitations of the lab. */
export async function getMicroMeta() {
  return (await client.get(`${BASE}/meta`)).data
}

export async function getMicroTopics() {
  return (await client.get(`${BASE}/topics`)).data
}

/** Every experiment with its question, claim and the What-If options it accepts. */
export async function getMicroExperiments() {
  return (await client.get(`${BASE}/experiments`)).data
}

/**
 * Creates a run. The ONLY inputs are an experiment id and What-If values, which the server validates against that experiment's own
 * options (a boolean, an integer inside its bounds, or one of its listed choices). Nothing else is sent.
 */
export async function createMicroRun(experimentId, whatIf) {
  return (await client.post(`${BASE}/runs`, { experimentId, whatIf })).data
}

/** Executes a CREATED run exactly once. A finished run returns its stored result; a run executing right now is a 409. */
export async function executeMicroRun(runId) {
  return (await client.post(`${BASE}/runs/${encodeURIComponent(runId)}/execute`)).data
}

export async function getMicroRun(runId) {
  return (await client.get(`${BASE}/runs/${encodeURIComponent(runId)}`)).data
}

/** Returns a finished run to CREATED (discarding its stored result) so it can be executed again on purpose. */
export async function resetMicroRun(runId) {
  return (await client.post(`${BASE}/runs/${encodeURIComponent(runId)}/reset`)).data
}

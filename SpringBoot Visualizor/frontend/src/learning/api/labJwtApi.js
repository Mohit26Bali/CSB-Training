import client from '../../api/client'
import { tracedFetch } from './labApi'

export async function getJwtCatalog() {
  return (await client.get('/api/lab/jwt/catalog')).data
}

export async function getComponentFacts() {
  return (await client.get('/api/lab/jwt/components')).data
}

/**
 * Asks the server for a LAB token of the chosen kind for the signed-in user. The server decides the subject (always
 * the caller); the client only chooses the fault and, optionally, the role the token will CLAIM.
 */
export async function issueToken(variant, claimedRole) {
  const body = { variant }
  if (claimedRole) body.claimedRole = claimedRole
  return (await client.post('/api/lab/jwt/issue', body)).data
}

/** Decodes and verifies any pasted token against the LAB key (a real application token will simply fail verification). */
export async function inspectToken(token) {
  return (await client.post('/api/lab/jwt/inspect', { token })).data
}

/**
 * Sends one real, traced GET to a lab-secure endpoint with the given LAB token (or none). The lab token is held in
 * memory only for this call. The learner's real access token is never sent to a lab-secure endpoint.
 */
export async function runLabCall(target, labToken) {
  const authorization = labToken ? `Bearer ${labToken}` : null
  return { target, ...(await tracedFetch(target.path, authorization)) }
}

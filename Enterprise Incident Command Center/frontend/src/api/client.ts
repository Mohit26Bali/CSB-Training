/**
 * The typed API client. This folder is the only place that talks to the backend (FRONTEND-PLAN).
 *
 * There is one client per backend service, each typed by the contract generated from that service
 * (src/api/generated). All of them call the gateway, so the paths are the public ones (/api/...).
 * A wrong path, a missing field or a wrong value is a compile error, not a surprise in the browser.
 */
import createClient, { type Middleware } from 'openapi-fetch';
import { API_BASE_URL } from './config';
import { ApiError } from './errors';
import { session } from './session';
import type { paths as AssignmentPaths } from './generated/assignment';
import type { paths as IncidentPaths } from './generated/incident';
import type { paths as MajorIncidentPaths } from './generated/major-incident';
import type { paths as RcaPaths } from './generated/rca';
import type { paths as DashboardPaths } from './generated/dashboard';

const LOGIN_PATH = '/api/auth/login';

/** Runs for every request of every client. */
const common: Middleware = {
  onRequest({ request }) {
    const token = session.getToken();
    if (token) {
      request.headers.set('Authorization', `Bearer ${token}`);
    }
    // One id per request. The backend keeps it through every service and writes it to the audit trails.
    request.headers.set('X-Correlation-Id', crypto.randomUUID());
    return request;
  },
  onResponse({ request, response }) {
    // 401 on any call except the login itself means the token is missing or expired.
    if (response.status === 401 && !request.url.endsWith(LOGIN_PATH)) {
      session.notifyUnauthorized();
    }
    return response;
  },
};

function make<P extends object>() {
  const client = createClient<P>({ baseUrl: API_BASE_URL });
  client.use(common);
  return client;
}

export const assignmentApi = make<AssignmentPaths>();
export const incidentApi = make<IncidentPaths>();
export const majorIncidentApi = make<MajorIncidentPaths>();
export const rcaApi = make<RcaPaths>();
export const dashboardApi = make<DashboardPaths>();

interface FetchResult<T> {
  data?: T;
  error?: unknown;
  response: Response;
}

/**
 * Turns the result of a client call into the data, or throws an ApiError.
 *   const teams = await unwrap(assignmentApi.GET('/api/teams'));
 * Screens and hooks use this, so they only ever deal with data or an ApiError.
 */
export async function unwrap<T>(call: Promise<FetchResult<T>>): Promise<T> {
  let result: FetchResult<T>;
  try {
    result = await call;
  } catch {
    throw ApiError.network();
  }
  if (!result.response.ok) {
    throw ApiError.fromResponse(result.response, result.error);
  }
  return result.data as T;
}

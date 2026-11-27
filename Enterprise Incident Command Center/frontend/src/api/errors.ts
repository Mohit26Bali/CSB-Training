/**
 * The one error type of the frontend. Every error the backend sends has the same JSON (common-lib ErrorResponse):
 *   { timestamp, status, code, message, path, correlationId }
 * The OpenAPI contract describes the successful answers only, so this shape is written here by hand.
 * It is the one place where the frontend repeats a backend shape; ENDPOINTS.md lists the codes.
 */
export type ErrorCode =
  | 'VALIDATION_FAILED'
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'CONFLICT'
  | 'SERVICE_UNAVAILABLE'
  | 'INTERNAL_ERROR'
  | 'NETWORK'; // frontend only: the server could not be reached at all

interface ErrorBody {
  status?: number;
  code?: string;
  message?: string;
  correlationId?: string | null;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: ErrorCode | string;
  readonly correlationId: string | null;

  constructor(status: number, code: ErrorCode | string, message: string, correlationId: string | null = null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.correlationId = correlationId;
  }

  /** Builds the error from a failed response. If the body is not our JSON, a generic message is used. */
  static fromResponse(response: Response, body: unknown): ApiError {
    const parsed = (typeof body === 'object' && body !== null ? body : {}) as ErrorBody;
    return new ApiError(
      response.status,
      parsed.code ?? 'INTERNAL_ERROR',
      parsed.message ?? `The server answered ${response.status}`,
      parsed.correlationId ?? response.headers.get('X-Correlation-Id'),
    );
  }

  static network(): ApiError {
    return new ApiError(0, 'NETWORK', 'The server cannot be reached. Check that the backend is running.');
  }

  /** 503: a service behind the gateway is down or too slow. The user can simply try again. */
  get isUnavailable(): boolean {
    return this.status === 503 || this.code === 'SERVICE_UNAVAILABLE' || this.code === 'NETWORK';
  }
}

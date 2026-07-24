# API error contract

`ApiError` is shared by servlet and reactive applications through the
framework-light `platform-contracts` module.

| Field | Type | Meaning |
| --- | --- | --- |
| `timestamp` | ISO-8601 UTC instant | Time the error response was constructed |
| `status` | integer | HTTP status code |
| `error` | string | Standard HTTP reason phrase |
| `code` | string | Stable machine-readable `ErrorCode` |
| `message` | string | Safe user-facing summary |
| `path` | string | Request path, without query secrets |
| `correlationId` | string | ID shared with response header and server logs |
| `fieldErrors` | array | Immutable field violations; never `null` |

Each field violation contains `field`, `message`, and nullable `rejectedValue`.
OrderFlow intentionally returns `null` for rejected values because passwords,
tokens, and other sensitive input must not be reflected.

## Validation error

```json
{
  "timestamp": "2026-07-24T01:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/example",
  "correlationId": "request-42",
  "fieldErrors": [
    {
      "field": "name",
      "message": "must not be blank",
      "rejectedValue": null
    }
  ]
}
```

## Resource not found

```json
{
  "timestamp": "2026-07-24T01:00:00Z",
  "status": 404,
  "error": "Not Found",
  "code": "RESOURCE_NOT_FOUND",
  "message": "The requested resource was not found",
  "path": "/missing",
  "correlationId": "request-43",
  "fieldErrors": []
}
```

## Downstream service unavailable

```json
{
  "timestamp": "2026-07-24T01:00:00Z",
  "status": 503,
  "error": "Service Unavailable",
  "code": "SERVICE_UNAVAILABLE",
  "message": "The requested service is temporarily unavailable",
  "path": "/api/platform/ping",
  "correlationId": "request-44",
  "fieldErrors": []
}
```

## Internal server error

```json
{
  "timestamp": "2026-07-24T01:00:00Z",
  "status": 500,
  "error": "Internal Server Error",
  "code": "INTERNAL_ERROR",
  "message": "An unexpected error occurred",
  "path": "/example",
  "correlationId": "request-45",
  "fieldErrors": []
}
```

Internal errors never expose exception messages, stack traces, Java types, SQL
text, credentials, or implementation details.

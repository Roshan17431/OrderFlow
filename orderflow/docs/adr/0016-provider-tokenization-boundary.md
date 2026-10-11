# ADR 0016: Provider tokenization boundary

## Status

Accepted

## Decision

OrderFlow accepts only opaque payment-method tokens. It rejects card numbers,
PAN, CVV/CVC, expiry fields, and bank credentials. The token is used only for a
confirmation call and is not persisted or logged; durable request
idempotency stores only a one-way normalized request hash.

Production payment data collection belongs in a provider-hosted/tokenizing UI.
The Phase 5 simulator uses named test tokens solely to exercise workflows.

## Rationale

Keeping raw card data outside the platform reduces accidental exposure and the
PCI data-handling surface. Tokenization alone does not make the application PCI
compliant, but accepting raw data would create a materially larger security
boundary without adding value to this phase.

## Consequences

Clients cannot submit card details directly. A future real provider adapter
must preserve the same boundary and use protected external credentials.

# Simulator payment boundary

The UI collects only the backend simulator outcome token (`tok_success`, `tok_declined`, `tok_requires_action`). It has no raw card number, CVV, expiry or real payment-provider fields. Confirmation uses the actual order endpoint and a retained idempotency key. An accepted `PROCESSING` response remains processing until backend workflow confirmation. A refund likewise remains pending/processing until a verified provider event succeeds.

Trusted local completion uses `orderflow/scripts/complete-simulator-payment.mjs`, outside the frontend bundle:

```sh
COMPOSE_PROJECT_NAME=orderflow-phase9 ORDERFLOW_SIMULATOR_HELPER=true \
  node scripts/complete-simulator-payment.mjs payment.succeeded PAYMENT_UUID
COMPOSE_PROJECT_NAME=orderflow-phase9 ORDERFLOW_SIMULATOR_HELPER=true \
  node scripts/complete-simulator-payment.mjs refund.succeeded REFUND_UUID
```

Invoke from the Maven project directory. The script is disabled by default, accepts only a localhost/127.0.0.1 gateway and allowed event kinds/UUIDs, and refuses production profiles or a disabled/non-simulator provider. It reads the simulator secret from the selected local payment-service container in memory, never prints it, signs the original JSON/timestamp with the normal HMAC format, and posts through the real gateway webhook verifier. There is no unsigned callback bypass, browser signing function or public environment secret. The console reports callback acceptance only; verify final state through the normal API/UI.

Admin refund input validates exact amount, supported reason and backend remaining balance minus currently reserved pending refunds. UI financial status and fulfillment status are independent; a partially refunded delivered order stays delivered. Real provider payments are outside this phase and require separate tokenization/provider configuration.

## Cloud demo completion

Phase10 adds an internal `cloud-demo` payment-service worker behind an explicit opt-in. It signs loopback callbacks to the existing verified webhook endpoint and leaves normal ledger, idempotency and outbox processing authoritative. `prod` and `production` both prohibit the simulator. No signing secret or completion endpoint is added to the browser.

The cloud build uses same-origin API paths. The customer UI clearly states that no real money is charged. A PROCESSING acknowledgement remains pending until the backend confirms the final result; local development still uses the trusted CLI when the demo worker is disabled. See the [Phase10 guide](../../docs/phase-10-cloud-deployment.md).

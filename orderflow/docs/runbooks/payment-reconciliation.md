# Payment reconciliation

## Symptoms

Payments remain pending past their deadline, provider and local states differ, reconciliation failure metrics rise, or paid/refunded totals disagree.

## Investigation

Use payment/order IDs, provider references, attempt status, webhook event IDs, outbox state, and correlation IDs. Compare amount/currency and provider state through approved provider tooling. Never log tokens, webhook secrets, card data, or raw signed bodies.

## Recovery and verification

Restore provider connectivity/signing configuration and allow the cluster-safe reconciliation job to claim bounded work. Do not force a success or refund by editing SQL. Verify a single terminal transition, correct cumulative refund total, matching order state, one outbox event, and stable inventory/delivery state. Escalate ambiguous capture, duplicate provider charge, amount mismatch, or missing provider record immediately.

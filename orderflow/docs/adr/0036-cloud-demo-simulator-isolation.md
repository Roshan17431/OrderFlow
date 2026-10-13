# ADR 0036: Explicit cloud demo simulator isolation

Status: Accepted for portfolio demo.

The public portfolio environment selects `cloud-demo` explicitly. Payment and message simulators run only under the demo policy; true production fails closed without a supported real provider. No browser receives webhook credentials, no public payment bypass is introduced, and internal signed callbacks retain normal idempotency and ownership behavior.

Release automation accepts only the named demo environment. Deployment approval does not enable real payments. The interface and documentation must make the simulation visible; cloud verification proves actual demo state transitions before marking a release healthy.

Consequences: the portfolio is not a production payment platform. Production enablement requires separate provider integration, security review and operational preparation.

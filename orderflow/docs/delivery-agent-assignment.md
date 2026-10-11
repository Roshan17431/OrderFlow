# Delivery-agent assignment

Agents register implicitly on their first availability update and begin `OFFLINE`. They may select `AVAILABLE` or `OFFLINE`; `BUSY` is controlled exclusively by assignment. A busy agent cannot abandon the active delivery by changing availability.

Automatic assignment selects the oldest `PENDING_ASSIGNMENT` delivery and the least recently assigned `AVAILABLE` agent. PostgreSQL `FOR UPDATE SKIP LOCKED`, entity versions, a unique order constraint, and transactional status changes prevent two agents from receiving one delivery or one agent from receiving concurrent active work. If no agent exists, the delivery remains pending. Making an agent available immediately attempts assignment, so there is no unbounded scheduled assignment poller.

Manual administrator assignment applies the same locks and availability checks. Assignment and the `DELIVERY_ASSIGNED` outbox record commit in one transaction.

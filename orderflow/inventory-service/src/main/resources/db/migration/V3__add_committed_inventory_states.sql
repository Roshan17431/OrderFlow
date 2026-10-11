ALTER TABLE inventory_reservation DROP CONSTRAINT ck_inventory_reservation_status;
ALTER TABLE inventory_reservation ADD CONSTRAINT ck_inventory_reservation_status
    CHECK (status IN ('RESERVED', 'COMMITTED', 'FULFILLED', 'REJECTED', 'RELEASED', 'EXPIRED'));

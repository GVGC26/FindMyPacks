CREATE TABLE packages
(
    id            BIGSERIAL PRIMARY KEY,
    tracking_code VARCHAR(20)  NOT NULL UNIQUE,
    customer      VARCHAR(100) NOT NULL,
    destination   VARCHAR(100) NOT NULL,
    carrier       VARCHAR(20)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    posted_at     DATE         NOT NULL,
    CONSTRAINT packages_carrier_check
        CHECK (carrier IN ('CORREIOS', 'JADLOG', 'LOGGI', 'OTHER')),
    CONSTRAINT packages_status_check
        CHECK (status IN ('POSTED', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED'))
);

CREATE INDEX idx_packages_customer ON packages (customer);
CREATE INDEX idx_packages_status ON packages (status);
CREATE INDEX idx_packages_carrier ON packages (carrier);
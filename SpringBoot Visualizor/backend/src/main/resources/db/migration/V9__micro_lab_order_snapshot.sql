-- Microservices Lab, Stage B3: the Order service keeps a SNAPSHOT of the unit price it was quoted. This is deliberate data
-- duplication: the Order service must not join to the Product service tables, so it stores what it needs and lives with the copy
-- becoming stale when the Product service later changes the price.
ALTER TABLE micro_lab_orders ADD COLUMN unit_price DECIMAL(10,2) NULL;

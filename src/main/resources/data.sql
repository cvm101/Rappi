-- Nodes: BLR-01 (healthy), BLR-02 (tight budget), BLR-03 (tight storage)

-- id, name, unit_volume, unit_cost
INSERT INTO product VALUES (1, 'Fresh Milk 1L', 1, 50);
INSERT INTO product VALUES (2, 'Basmati Rice 5kg', 2, 400);
INSERT INTO product VALUES (3, 'Cooking Oil 1L', 1, 150);
INSERT INTO product VALUES (4, 'Coffee Beans 500g', 1, 300);
INSERT INTO product VALUES (5, 'Detergent 5L', 4, 100);
INSERT INTO product VALUES (6, 'Eggs Tray 30', 1, 200);
INSERT INTO product VALUES (7, 'Ice Cream Tub', 1, 120);

-- id, product, node, on_hand
INSERT INTO inventory VALUES (1, 1, 'BLR-01', 40);
INSERT INTO inventory VALUES (2, 2, 'BLR-01', 600);
INSERT INTO inventory VALUES (3, 3, 'BLR-01', 200);
INSERT INTO inventory VALUES (4, 4, 'BLR-02', 40);
INSERT INTO inventory VALUES (5, 5, 'BLR-03', 40);
INSERT INTO inventory VALUES (6, 6, 'BLR-01', 20);
INSERT INTO inventory VALUES (7, 7, 'BLR-01', 10);

-- id, product, node, daily_demand
INSERT INTO demand_forecast VALUES (1, 1, 'BLR-01', 70);
INSERT INTO demand_forecast VALUES (2, 2, 'BLR-01', 20);
INSERT INTO demand_forecast VALUES (3, 3, 'BLR-01', 50);
INSERT INTO demand_forecast VALUES (4, 4, 'BLR-02', 70);
INSERT INTO demand_forecast VALUES (5, 5, 'BLR-03', 70);
INSERT INTO demand_forecast VALUES (6, 6, 'BLR-01', 40);
INSERT INTO demand_forecast VALUES (7, 7, 'BLR-01', 30);

-- id, node, remaining_amount
INSERT INTO budget VALUES (1, 'BLR-01', 500000);
INSERT INTO budget VALUES (2, 'BLR-02', 90000);
INSERT INTO budget VALUES (3, 'BLR-03', 500000);

-- id, node, capacity_volume, used_volume
INSERT INTO storage_capacity VALUES (1, 'BLR-01', 10000, 3000);
INSERT INTO storage_capacity VALUES (2, 'BLR-02', 10000, 1000);
INSERT INTO storage_capacity VALUES (3, 'BLR-03', 2000, 600);

-- id, name, lead_time_days, moq, reliability
INSERT INTO supplier VALUES (1, 'Prime Foods', 3, 100, 0.95);
INSERT INTO supplier VALUES (2, 'Metro Wholesale', 5, 150, 0.88);
INSERT INTO supplier VALUES (3, 'QuickSupply', 2, 300, 0.80);

-- id, supplier, product, unit_price, available_qty
INSERT INTO supplier_product VALUES (1, 1, 1, 50, 2000);
INSERT INTO supplier_product VALUES (2, 1, 2, 400, 1000);
INSERT INTO supplier_product VALUES (3, 1, 3, 150, 2000);
INSERT INTO supplier_product VALUES (4, 1, 4, 300, 2000);
INSERT INTO supplier_product VALUES (5, 1, 5, 100, 2000);
INSERT INTO supplier_product VALUES (6, 1, 6, 200, 0);
INSERT INTO supplier_product VALUES (7, 1, 7, 120, 50);
INSERT INTO supplier_product VALUES (8, 2, 1, 52, 1500);
INSERT INTO supplier_product VALUES (9, 2, 3, 155, 1500);
INSERT INTO supplier_product VALUES (10, 2, 6, 190, 1000);
INSERT INTO supplier_product VALUES (11, 3, 1, 55, 1000);
INSERT INTO supplier_product VALUES (12, 3, 3, 160, 1000);
INSERT INTO supplier_product VALUES (13, 3, 6, 210, 1000);

-- Existing open POs: #1 cooking oil 300 units (scenario: open PO already covers part of the need)
--                    #2 eggs 500 units (scenario: supplier will only confirm 250)
INSERT INTO purchase_order (id, product_id, supplier_id, node_id, quantity, unit_price, status, source, note, created_at)
VALUES (1, 3, 1, 'BLR-01', 300, 150, 'OPEN', 'SYSTEM', 'Existing open PO', CURRENT_TIMESTAMP);
INSERT INTO purchase_order (id, product_id, supplier_id, node_id, quantity, unit_price, status, source, note, created_at)
VALUES (2, 6, 1, 'BLR-01', 500, 200, 'OPEN', 'SYSTEM', 'Existing open PO', CURRENT_TIMESTAMP);

ALTER TABLE purchase_order ALTER COLUMN id RESTART WITH 1000;

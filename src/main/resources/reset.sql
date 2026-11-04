-- DML-only demo reset, executed by DemoDataService.reset() (no DDL: on a persistent
-- Postgres like Supabase, DROP/CREATE mid-session via the pooler is not reliable;
-- tables already exist from startup init). The agent audit log is NOT touched.
DELETE FROM purchase_order;
DELETE FROM supplier_product;
DELETE FROM supplier;
DELETE FROM storage_capacity;
DELETE FROM budget;
DELETE FROM demand_forecast;
DELETE FROM inventory;
DELETE FROM product;

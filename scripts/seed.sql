-- seed.sql: insert sample data
PRAGMA foreign_keys = ON;

-- make idempotent: temporarily disable foreign keys to allow clean deletes then re-enable
PRAGMA foreign_keys = OFF;
DELETE FROM visit_daily;
DELETE FROM budget_factors;
DELETE FROM budget_monthly;
DELETE FROM store;
DELETE FROM company;
-- reset AUTOINCREMENT counters so inserted ids start from 1
DELETE FROM sqlite_sequence WHERE name IN ('company','store','visit_daily','budget_monthly','budget_factors');
PRAGMA foreign_keys = ON;

-- insert 5 companies
INSERT INTO company (company_code, name) VALUES ('C001', 'Company 1');
INSERT INTO company (company_code, name) VALUES ('C002', 'Company 2');
INSERT INTO company (company_code, name) VALUES ('C003', 'Company 3');
INSERT INTO company (company_code, name) VALUES ('C004', 'Company 4');
INSERT INTO company (company_code, name) VALUES ('C005', 'Company 5');

-- insert 5 stores (assign to company ids 1..5)
INSERT INTO store (store_code, company_id, name, country, brand, open_date) VALUES ('S001', 1, 'Store 1', 'JP', 'BrandA', '2020-01-01');
INSERT INTO store (store_code, company_id, name, country, brand, open_date) VALUES ('S002', 2, 'Store 2', 'JP', 'BrandA', '2020-01-02');
INSERT INTO store (store_code, company_id, name, country, brand, open_date) VALUES ('S003', 3, 'Store 3', 'JP', 'BrandB', '2020-01-03');
INSERT INTO store (store_code, company_id, name, country, brand, open_date) VALUES ('S004', 4, 'Store 4', 'JP', 'BrandB', '2020-01-04');
INSERT INTO store (store_code, company_id, name, country, brand, open_date) VALUES ('S005', 5, 'Store 5', 'JP', 'BrandC', '2020-01-05');

-- insert 5 budget_monthly rows per store for month '2025-09'
INSERT INTO budget_monthly (store_id, year_month, budget_amount) VALUES (1, '2025-09', 1000);
INSERT INTO budget_monthly (store_id, year_month, budget_amount) VALUES (2, '2025-09', 1100);
INSERT INTO budget_monthly (store_id, year_month, budget_amount) VALUES (3, '2025-09', 1200);
INSERT INTO budget_monthly (store_id, year_month, budget_amount) VALUES (4, '2025-09', 1300);
INSERT INTO budget_monthly (store_id, year_month, budget_amount) VALUES (5, '2025-09', 1400);

-- insert 5 budget_factors rows per store
INSERT INTO budget_factors (store_id, year_month, factor_json) VALUES (1, '2025-09', json('{"factor":1.0}'));
INSERT INTO budget_factors (store_id, year_month, factor_json) VALUES (2, '2025-09', json('{"factor":1.1}'));
INSERT INTO budget_factors (store_id, year_month, factor_json) VALUES (3, '2025-09', json('{"factor":1.2}'));
INSERT INTO budget_factors (store_id, year_month, factor_json) VALUES (4, '2025-09', json('{"factor":1.3}'));
INSERT INTO budget_factors (store_id, year_month, factor_json) VALUES (5, '2025-09', json('{"factor":1.4}'));

-- visit_daily: we'll rely on scripts/seed.ps1 to bulk-insert 100 rows using a loop

-- end

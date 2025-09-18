-- 001_init.sql: initial schema and trigger skeletons (idempotent)
PRAGMA foreign_keys = ON;

-- schema_version table for migration tracking
CREATE TABLE IF NOT EXISTS schema_version (
  version TEXT PRIMARY KEY,
  applied_at TEXT,
  script_name TEXT,
  checksum TEXT
);

-- company
CREATE TABLE IF NOT EXISTS company (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  company_code TEXT UNIQUE,
  name TEXT,
  status TEXT DEFAULT 'active',
  created_at TEXT DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_company_company_code ON company(company_code);

-- store
CREATE TABLE IF NOT EXISTS store (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  store_code TEXT UNIQUE,
  company_id INTEGER,
  name TEXT,
  country TEXT,
  brand TEXT,
  open_date TEXT,
  close_date TEXT,
  status TEXT DEFAULT 'open',
  created_at TEXT DEFAULT (datetime('now')),
  FOREIGN KEY(company_id) REFERENCES company(id)
);
CREATE INDEX IF NOT EXISTS idx_store_store_code ON store(store_code);
CREATE INDEX IF NOT EXISTS idx_store_company_id ON store(company_id);

-- visit_daily
CREATE TABLE IF NOT EXISTS visit_daily (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  store_id INTEGER NOT NULL,
  visit_date TEXT NOT NULL,
  visitors INTEGER DEFAULT 0,
  created_at TEXT DEFAULT (datetime('now')),
  FOREIGN KEY(store_id) REFERENCES store(id)
);
CREATE INDEX IF NOT EXISTS idx_visit_daily_store_date ON visit_daily(store_id, visit_date);

-- budget_monthly
CREATE TABLE IF NOT EXISTS budget_monthly (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  store_id INTEGER NOT NULL,
  year_month TEXT NOT NULL,
  budget_amount INTEGER DEFAULT 0,
  created_at TEXT DEFAULT (datetime('now')),
  FOREIGN KEY(store_id) REFERENCES store(id)
);
CREATE INDEX IF NOT EXISTS idx_budget_monthly_store_year ON budget_monthly(store_id, year_month);

-- budget_factors
CREATE TABLE IF NOT EXISTS budget_factors (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  store_id INTEGER,
  year_month TEXT NOT NULL,
  factor_json TEXT,
  created_at TEXT DEFAULT (datetime('now')),
  FOREIGN KEY(store_id) REFERENCES store(id)
);
CREATE INDEX IF NOT EXISTS idx_budget_factors_store_year ON budget_factors(store_id, year_month);

-- change_log
-- change_log
CREATE TABLE IF NOT EXISTS change_log (
  lsn INTEGER PRIMARY KEY AUTOINCREMENT,
  tx_id TEXT,
  table_name TEXT,
  pk_json TEXT,
  op TEXT,
  payload TEXT,
  tombstone INTEGER DEFAULT 0,
  created_at TEXT DEFAULT (datetime('now')),
  source_node TEXT
);
CREATE INDEX IF NOT EXISTS idx_changelog_table_lsn ON change_log(table_name, lsn);

-- sync_state
CREATE TABLE IF NOT EXISTS sync_state (
  client_id TEXT PRIMARY KEY,
  last_lsn INTEGER,
  updated_at TEXT DEFAULT (datetime('now'))
);

-- id_map (for client temp id mapping)
CREATE TABLE IF NOT EXISTS id_map (
  client_id TEXT,
  client_temp_id TEXT,
  main_id INTEGER,
  created_at TEXT DEFAULT (datetime('now')),
  PRIMARY KEY (client_id, client_temp_id)
);

-- trigger helper: ensure DROP IF EXISTS then CREATE
-- company triggers
DROP TRIGGER IF EXISTS trg_company_insert;
CREATE TRIGGER trg_company_insert AFTER INSERT ON company
BEGIN
  INSERT INTO change_log (tx_id, table_name, pk_json, op, payload, tombstone, created_at, source_node)
  VALUES (lower(hex(randomblob(16))), 'company', json_object('id', NEW.id), 'I', json_object('id', NEW.id, 'company_code', NEW.company_code, 'name', NEW.name), 0, datetime('now'), 'local');
END;

DROP TRIGGER IF EXISTS trg_company_update;
CREATE TRIGGER trg_company_update AFTER UPDATE ON company
BEGIN
  INSERT INTO change_log (tx_id, table_name, pk_json, op, payload, tombstone, created_at, source_node)
  VALUES (lower(hex(randomblob(16))), 'company', json_object('id', NEW.id), 'U', json_object('id', NEW.id, 'company_code', NEW.company_code, 'name', NEW.name), 0, datetime('now'), 'local');
END;

DROP TRIGGER IF EXISTS trg_company_delete;
CREATE TRIGGER trg_company_delete AFTER DELETE ON company
BEGIN
  INSERT INTO change_log (tx_id, table_name, pk_json, op, payload, tombstone, created_at, source_node)
  VALUES (lower(hex(randomblob(16))), 'company', json_object('id', OLD.id), 'D', json_object('id', OLD.id, 'company_code', OLD.company_code, 'name', OLD.name), 1, datetime('now'), 'local');
END;

-- store triggers
DROP TRIGGER IF EXISTS trg_store_insert;
CREATE TRIGGER trg_store_insert AFTER INSERT ON store
BEGIN
  INSERT INTO change_log (tx_id, table_name, pk_json, op, payload, tombstone, created_at, source_node)
  VALUES (lower(hex(randomblob(16))), 'store', json_object('id', NEW.id), 'I', json_object('id', NEW.id, 'store_code', NEW.store_code, 'company_id', NEW.company_id, 'name', NEW.name), 0, datetime('now'), 'local');
END;

DROP TRIGGER IF EXISTS trg_store_update;
CREATE TRIGGER trg_store_update AFTER UPDATE ON store
BEGIN
  INSERT INTO change_log (tx_id, table_name, pk_json, op, payload, tombstone, created_at, source_node)
  VALUES (lower(hex(randomblob(16))), 'store', json_object('id', NEW.id), 'U', json_object('id', NEW.id, 'store_code', NEW.store_code, 'company_id', NEW.company_id, 'name', NEW.name), 0, datetime('now'), 'local');
END;

DROP TRIGGER IF EXISTS trg_store_delete;
CREATE TRIGGER trg_store_delete AFTER DELETE ON store
BEGIN
  INSERT INTO change_log (tx_id, table_name, pk_json, op, payload, tombstone, created_at, source_node)
  VALUES (lower(hex(randomblob(16))), 'store', json_object('id', OLD.id), 'D', json_object('id', OLD.id, 'store_code', OLD.store_code, 'company_id', OLD.company_id, 'name', OLD.name), 1, datetime('now'), 'local');
END;

-- visit_daily triggers
DROP TRIGGER IF EXISTS trg_visit_daily_insert;
CREATE TRIGGER trg_visit_daily_insert AFTER INSERT ON visit_daily
BEGIN
  INSERT INTO change_log (tx_id, table_name, pk_json, op, payload, tombstone, created_at, source_node)
  VALUES (lower(hex(randomblob(16))), 'visit_daily', json_object('id', NEW.id), 'I', json_object('id', NEW.id, 'store_id', NEW.store_id, 'visit_date', NEW.visit_date, 'visitors', NEW.visitors), 0, datetime('now'), 'local');
END;

DROP TRIGGER IF EXISTS trg_visit_daily_update;
CREATE TRIGGER trg_visit_daily_update AFTER UPDATE ON visit_daily
BEGIN
  INSERT INTO change_log (tx_id, table_name, pk_json, op, payload, tombstone, created_at, source_node)
  VALUES (lower(hex(randomblob(16))), 'visit_daily', json_object('id', NEW.id), 'U', json_object('id', NEW.id, 'store_id', NEW.store_id, 'visit_date', NEW.visit_date, 'visitors', NEW.visitors), 0, datetime('now'), 'local');
END;

DROP TRIGGER IF EXISTS trg_visit_daily_delete;
CREATE TRIGGER trg_visit_daily_delete AFTER DELETE ON visit_daily
BEGIN
  INSERT INTO change_log (tx_id, table_name, pk_json, op, payload, tombstone, created_at, source_node)
  VALUES (lower(hex(randomblob(16))), 'visit_daily', json_object('id', OLD.id), 'D', json_object('id', OLD.id, 'store_id', OLD.store_id, 'visit_date', OLD.visit_date, 'visitors', OLD.visitors), 1, datetime('now'), 'local');
END;

-- budget_monthly triggers
DROP TRIGGER IF EXISTS trg_budget_monthly_insert;
CREATE TRIGGER trg_budget_monthly_insert AFTER INSERT ON budget_monthly
BEGIN
  INSERT INTO change_log (tx_id, table_name, pk_json, op, payload, tombstone, created_at, source_node)
  VALUES (lower(hex(randomblob(16))), 'budget_monthly', json_object('id', NEW.id), 'I', json_object('id', NEW.id, 'store_id', NEW.store_id, 'year_month', NEW.year_month, 'budget_amount', NEW.budget_amount), 0, datetime('now'), 'local');
END;

DROP TRIGGER IF EXISTS trg_budget_monthly_update;
CREATE TRIGGER trg_budget_monthly_update AFTER UPDATE ON budget_monthly
BEGIN
  INSERT INTO change_log (tx_id, table_name, pk_json, op, payload, tombstone, created_at, source_node)
  VALUES (lower(hex(randomblob(16))), 'budget_monthly', json_object('id', NEW.id), 'U', json_object('id', NEW.id, 'store_id', NEW.store_id, 'year_month', NEW.year_month, 'budget_amount', NEW.budget_amount), 0, datetime('now'), 'local');
END;

DROP TRIGGER IF EXISTS trg_budget_monthly_delete;
CREATE TRIGGER trg_budget_monthly_delete AFTER DELETE ON budget_monthly
BEGIN
  INSERT INTO change_log (tx_id, table_name, pk_json, op, payload, tombstone, created_at, source_node)
  VALUES (lower(hex(randomblob(16))), 'budget_monthly', json_object('id', OLD.id), 'D', json_object('id', OLD.id, 'store_id', OLD.store_id, 'year_month', OLD.year_month, 'budget_amount', OLD.budget_amount), 1, datetime('now'), 'local');
END;

-- budget_factors triggers
DROP TRIGGER IF EXISTS trg_budget_factors_insert;
CREATE TRIGGER trg_budget_factors_insert AFTER INSERT ON budget_factors
BEGIN
  INSERT INTO change_log (tx_id, table_name, pk_json, op, payload, tombstone, created_at, source_node)
  VALUES (lower(hex(randomblob(16))), 'budget_factors', json_object('id', NEW.id), 'I', json_object('id', NEW.id, 'store_id', NEW.store_id, 'year_month', NEW.year_month, 'factor_json', NEW.factor_json), 0, datetime('now'), 'local');
END;

DROP TRIGGER IF EXISTS trg_budget_factors_update;
CREATE TRIGGER trg_budget_factors_update AFTER UPDATE ON budget_factors
BEGIN
  INSERT INTO change_log (tx_id, table_name, pk_json, op, payload, tombstone, created_at, source_node)
  VALUES (lower(hex(randomblob(16))), 'budget_factors', json_object('id', NEW.id), 'U', json_object('id', NEW.id, 'store_id', NEW.store_id, 'year_month', NEW.year_month, 'factor_json', NEW.factor_json), 0, datetime('now'), 'local');
END;

DROP TRIGGER IF EXISTS trg_budget_factors_delete;
CREATE TRIGGER trg_budget_factors_delete AFTER DELETE ON budget_factors
BEGIN
  INSERT INTO change_log (tx_id, table_name, pk_json, op, payload, tombstone, created_at, source_node)
  VALUES (lower(hex(randomblob(16))), 'budget_factors', json_object('id', OLD.id), 'D', json_object('id', OLD.id, 'store_id', OLD.store_id, 'year_month', OLD.year_month, 'factor_json', OLD.factor_json), 1, datetime('now'), 'local');
END;

-- record this migration in schema_version
INSERT OR REPLACE INTO schema_version (version, applied_at, script_name, checksum)
VALUES ('001', datetime('now'), '001_init.sql', lower(hex(randomblob(8))));

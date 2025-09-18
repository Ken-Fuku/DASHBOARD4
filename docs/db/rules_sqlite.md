# DB_RULES_SQLITE (原案)

目的: SQLite の運用ルール、PRAGMA 設定、DDL 命名規約を定義する。

## 基本方針
- Main / Client 共に SQLite ファイルを使用。WAL モードを基本とする。
- マイグレーションは SQL スクリプトで管理し、冪等にする。
- 年度分割は必要に応じて DB ファイル単位で行う（ファイル名に年度を含める）。

## 推奨 PRAGMA（起動時に必ず適用）
- PRAGMA journal_mode = WAL;
- PRAGMA synchronous = NORMAL; (整合が厳しければ FULL)
- PRAGMA foreign_keys = ON;
- PRAGMA temp_store = MEMORY;
- PRAGMA page_size = 4096;
- PRAGMA cache_size = -2000;

実装箇所: SqlitePragmaConfigurer (MigrationRunner/ConnectionManager 起動時に適用)

## VACUUM とバックアップ
- 定期保守: 週次で VACUUM INTO を実行しホットコピーを作成
- バックアップ: 日次フル + 週次フル保持

## DDL / 命名規約
- テーブル: snake_case (例: company, store, visit_daily, change_log)
- 主キー: id INTEGER PRIMARY KEY AUTOINCREMENT (業務キーは UNIQUE)
- 外部キー列: {referenced_table}_id (例: company_id)
- インデックス: idx_{table}_{column}
- トリガ: trg_{table}_{event}_{purpose}

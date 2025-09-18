# DB_RULES_SQLITE (原案)

目的: SQLite の運用ルール、PRAGMA 設定、DDL 命名規約を定義する。

## 基本方針
- Main / Client 共に SQLite ファイルを使用。WAL モードを基本とする。
- マイグレーションは SQL スクリプトで管理し、冪等にする。
- 年度分割は必要に応じ DB ファイル単位で行う（ファイル名に年度を含める）。

## 推奨 PRAGMA（起動時に必ず適用）
- PRAGMA journal_mode = WAL;
- PRAGMA synchronous = NORMAL; （高整合性が必要なら FULL）
- PRAGMA foreign_keys = ON;
- PRAGMA temp_store = MEMORY;
- PRAGMA page_size = 4096;（必要に応じ）
- PRAGMA cache_size = -2000;（例、メモリ単位で調整）

実装箇所: SqlitePragmaConfigurer（MigrationRunner/ConnectionManager 起動時に適用）

## VACUUM とバックアップ
- 定期保守: 週次で VACUUM INTO を行いホットコピーを作成（リストア検証を月次で実施）
- バックアップ: 日次フルバックアップ + 週次保持（運用ポリシーに従う）

## DDL / 命名規約
- テーブル: snake_case（例: company, store, visit_daily, change_log）
- 主キー: id INTEGER PRIMARY KEY AUTOINCREMENT（業務キーは UNIQUE 制約で別途保持）
- 外部キー列: {referenced_table}_id（例: company_id）
- インデックス: idx_{table}_{column}（複合は idx_{table}_{col1}_{col2}）
- トリガ: trg_{table}_{event}_{purpose}（例: trg_visit_daily_insert_changelog）

## マイグレーション
- ファイル命名: 001_init.sql, 002_add_unique_keys.sql...
- スクリプトは冪等（IF NOT EXISTS）で記述
- MigrationRunner は schema_version テーブルを更新し、スクリプト SHA256 を記録（検証用）

## 性能上の注意
- 大量 INSERT はトランザクションでまとめる
- バルク適用時は PRAGMA synchronous を一時的に変更する運用案を検討（ただし障害時のリスクは評価）
- SELECT 集計用に必要なインデックスを事前に追加する

## トリガ & ChangeLog
- 実テーブルの I/U/D トリガで change_log にエントリを記録する（payload を JSON にする）
- トリガ内の処理は軽量化（大きな処理は外部バッチで）

## 実装差分（M1 実施時の注意）
- PRAGMA の適用は `SqlitePragmaConfigurer` を通じて行う。`MigrationRunner` 起動時や接続確立後に適用することで WAL や foreign_keys の設定が確実に有効になる。
- CI/統合テスト環境では外部 `sqlite3` CLI に依存しないよう、シード処理は JDBC（`sqlite-jdbc`）で実行する実装を推奨する。PowerShell スクリプト `scripts/seed.ps1` はローカル手順用であり、Windows 環境で `sqlite3` が PATH にあることが前提となる。
- トリガが JSON 関連関数（`json_object` 等）を使う場合、SQLite のビルドによっては関数の有無に差があるため、CI 環境上での確認が重要（今回の M1 では sqlite-jdbc を使ったテストで検証済み）。


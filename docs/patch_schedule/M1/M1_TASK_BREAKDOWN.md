# M1 タスク分解（詳細）

目的: M1（スケルトン + マイグレーション + サンプルデータ）を 10 営業日で完了するための作業単位分解。各タスク完了時に「実行したポイント」と「満たした根拠（証跡）」を残す形式。

---

## 目次
1. 前準備
2. Day2: app-core モジュール雛形
3. Day3: MigrationRunner と SqliteConnectionManager
4. Day4: SqlitePragmaConfigurer
5. Day5: 001_init.sql（DDL）作成
6. Day6: トリガと ChangeLogRepository
7. Day7: SeedService / seeds / CLI
8. Day8: ApportionService 単体テスト
9. Day9: 統合テスト & CI 初版
10. Day10: ドキュメント整備と受け入れ確認

---

### タスク 0: 前準備（Day1）
- 作業内容
  - リポジトリ最新取得、JDK/Maven のバージョン確認、VS Code の設定、必要ライブラリ確認
- 完了のポイント（実行したこと）
  - `mvn -v` と `java -version` の実行結果をスクリーンショットまたはログに保存
  - VS Code の推奨拡張（Language Support for Java, Maven for Java, EditorConfig for VS Code）をインストール
- 満たした根拠（証跡）
  - `tools/env_check.txt` にバージョン情報を記載
  - `.vscode/extensions.json` に推奨拡張を追加

---

### タスク 1: app-core モジュール雛形（Day2）
- 作業内容
  - ルート `pom.xml` と `app-core/pom.xml` の作成
  - 主要ディレクトリ作成: `app-core/src/main/java`, `app-core/src/main/resources`, `app-core/src/test/java`
  - 最小の `Main` クラスと `application.properties` を配置
- 完了のポイント（実行したこと）
  - `mvn -pl app-core -am package` が成功することを確認
  - 生成された JAR の存在（`app-core/target/*.jar`）を確認
- 満たした根拠（証跡）
  - `build/app_core_build_log.txt` に `mvn package` の出力を保存
  - `app-core/pom.xml` と `app-core/src/main/java/com/kenfukuda/dashboard/cli/Main.java` が存在

---

### タスク 2: MigrationRunner と SqliteConnectionManager（Day3）
- 作業内容
  - `MigrationRunner` の実装（`db/migrations/*.sql` を順に適用、`schema_version` 更新）
  - `SqliteConnectionManager`：接続プール/シングルトンのラッパ実装
- 完了のポイント（実行したこと）
  - 空の/サンプルのマイグレーションスクリプトを作成して `MigrationRunner` で適用
  - `schema_version` にマイグレーション適用結果が記録される
- 満たした根拠（証跡）
  - `app-core/target/logs/migration_run.log` に適用ログを保存
  - DB ファイルの `schema_version` テーブルをクエリして結果を保存（`tools/migration_check.sql` 実行結果）

---

### タスク 3: SqlitePragmaConfigurer（Day4）
- 作業内容
  - アプリ起動時に PRAGMA を適用する `SqlitePragmaConfigurer` を実装
  - PRAGMA 設定（WAL、synchronous、foreign_keys、temp_store など）を `application.properties` で管理
- 完了のポイント（実行したこと）
  - 起動時ログから PRAGMA の設定確認ができる
  - `PRAGMA journal_mode` 等の現在値をクエリしてファイルに保存
- 満たした根拠（証跡）
  - `app-core/target/logs/pragma_apply.log` に適用ログを保存
  - 実際の DB に `PRAGMA journal_mode` の結果が `WAL` か確認し `tools/pragma_check.txt` に保存

---

### タスク 4: 001_init.sql（DDL）作成（Day5）
- 作業内容
  - マスタテーブル（company, store, ...）とトランザクションテーブル（visit_daily, budget_monthly）
  - 変更ログ用 `change_log`（lsn, tx_id, table_name, pk_json, op, payload, tombstone, created_at）
  - `sync_state` テーブル
  - トリガ雛形（INSERT/UPDATE/DELETE で change_log に書き込む）
- 完了のポイント（実行したこと）
  - `db/migrations/001_init.sql` を MigrationRunner で適用
  - 各テーブルが存在し、基本制約（UNIQUE/FOREIGN KEY）が付与されていることを確認
- 満たした根拠（証跡）
  - `tools/schema_dump.sql` に `sqlite_master` から出力したDDLを保存
  - `app-core/target/logs/migration_run.log` に 001_init.sql 適用ログを保存

---

### タスク 5: トリガ実装と ChangeLogRepository（Day6）
- 作業内容
  - 各実テーブルのトリガを詳細実装（payload JSON 形成、tx_id の割当）
  - `ChangeLogRepository` の CRUD と `getChanges(from_lsn, to_lsn)` を実装
- 完了のポイント（実行したこと）
  - INSERT/UPDATE/DELETE を行い、`change_log` に正しいエントリが追加されることを確認
  - `ChangeLogRepository.getChanges` で期待する差分が取得できる
- 満たした根拠（証跡）
  - `tools/changelog_test.sql`（INSERT→SELECT change_log）とその出力ログ保存
  - `app-core/target/logs/changelog_repo.log` に操作ログを保存

---

### タスク 6: SeedService / seeds / CLI（Day7）
- 作業内容
  - `db/seeds/*.sql`（最小サンプルデータ）を作成
  - `SeedService` と `SeedCommand`（CLI）を実装
  - `run-cli` / PowerShell スクリプトから `migrate` と `seed` を実行できるようにする
- 完了のポイント（実行したこと）
  - `scripts\migrate.ps1` を実行して DB 作成 → `scripts\seed.ps1` でサンプル投入
  - サンプル投入後に簡易クエリ（例: SELECT count(*) FROM visit_daily）で期待件数を確認
- 満たした根拠（証跡）
  - `tools/seed_result.txt` にクエリ結果を保存
  - `scripts\seed.ps1` の実行ログを `app-core/target/logs/seed.log` に保存

---

### タスク 7: ApportionService（按分）単体テスト（Day8）
- 作業内容
  - Hamilton 法の実装（端数処理含む）
  - 単体テスト（基本ケース、端数境界、ゼロ係数等）を作成
- 完了のポイント（実行したこと）
  - 単体テストがローカルで成功し、CI に組み込む
- 満たした根拠（証跡）
  - `app-core/target/surefire-reports/` にテスト結果が出力
  - テストコード: `src/test/java/.../ApportionServiceTest.java`

---

### タスク 8: 統合テスト & CI 初版（Day9）
- 作業内容
  - Integration テスト（Migration → Seed → ReportQuery の簡易フロー）を実装
  - `.github/workflows/ci.yml` の作成: `mvn test` を実行
- 完了のポイント（実行したこと）
  - CI が PR に対して `mvn test` を実行し、テストが成功する
  - ローカルで `mvn test` が成功する
- 満たした根拠（証跡）
  - GitHub Actions のワークフロー実行ログ（成功）へのリンク（または保存）
  - `app-core/target/logs/ci_test_run.log` に CI 実行ログを保存

---

### タスク 9: ドキュメント整備・受け入れ確認（Day10）
- 作業内容
  - README.md の M1 手順更新
  - docs の該当箇所（DB_RULES_SQLITE.md 等）に実装差分を反映
  - M1 のデモ（手順書）を作成して関係者へ共有
- 完了のポイント（実行したこと）
  - README に M1 実行手順を記載し、ローカルで手順を再現して確認
  - M1 完了報告書（チェックリスト）を作成
- 満たした根拠（証跡）
  - `docs/README_M1.md`（操作手順）を作成
  - `docs/patch_schedule/M1/acceptance_checklist.md` に完了チェックを記載

---

## 補足: 記録の保管先
- すべてのログ/チェックは `tools/` または `app-core/target/logs/` に格納するルール
- 重要成果物（migrations, seeds, DDL）は `db/` 以下でバージョン管理

---

作成済み: `docs/patch_schedule/M1/M1_TASK_BREAKDOWN.md`

次はこのタスクを GitHub Issues にチケット分割して出力しますか？または "Day2 の app-core 雛形作成" を私が実行してもよいです。
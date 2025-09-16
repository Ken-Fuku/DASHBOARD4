# M1 バッチスケジュール（原案）

目的: M1（スケルトン + マイグレーション + サンプルデータ）を確実に完了するための実行計画。期間想定: 2 週間（10 営業日）※リソースにより調整可。

---

## 前提
- ドキュメント: docs/ 以下の規約（ARCHITECTURE..., DB_RULES_SQLITE.md, DB_SYNC_POLICY.md, TEST_POLICY.md, PROJECT_RULES.md）に準拠する。
- OS: Windows 開発環境（PowerShell スクリプトを用意）
- 主要成果物: app-core Maven モジュール雛形、MigrationRunner、001_init.sql（DDL + triggers）、SqlitePragmaConfigurer、SeedService/seed SQL、CLI（migrate/seed/sync コマンド）、CI ワークフロー、基本ユニット/統合テスト。

---

## 高レベルスケジュール（10営業日案）

Week 1
- Day 1: キックオフ & 環境整備（半日）
  - タスク: リポジトリ最新取得、JDK/Maven 確認、VS Code 設定、必要ライブラリ確認
  - 成果物: dev 環境で `mvn -v` / `java -version` が OK
- Day 2: app-core モジュール雛形 + pom.xml（1日）
  - タスク: ルート pom、app-core/pom 作成、ディレクトリ作成（src/main/java, resources, test）
  - 成果物: mvn package が成功する最小雛形
- Day 3: MigrationRunner と SqliteConnectionManager（1日）
  - タスク: MigrationRunner（db/migrations/*.sql 実行）、SqliteConnectionManager（接続ラッパ）
  - 成果物: MigrationRunner による空スクリプト適用が成功
- Day 4: SqlitePragmaConfigurer + PRAGMA 適用の自動化（1日）
  - タスク: PRAGMA（WAL, foreign_keys, synchronous など）適用実装と起動フロー組込み
  - 成果物: 起動時に PRAGMA が反映される（ログ出力で検証）
- Day 5: 001_init.sql（DDL）作成 — マスタ/取引/同期用（1日）
  - タスク: company, store, visit_daily, budget_monthly, change_log（指定カラム）,
    sync_state テーブル、トリガ雛形を作成
  - 成果物: db/migrations/001_init.sql（冪等）を用いたマイグレーション適用確認

Week 2
- Day 6: トリガ実装（change_log 書き込み） + ChangeLogRepository（1日）
  - タスク: 各テーブル用トリガ SQL、ChangeLogRepository の読み書き API
  - 成果物: INSERT/UPDATE/DELETE が change_log に記録されることを確認
- Day 7: SeedService / seeds 作成 + SeedCommand（半日） & CLI 構成（残り半日）
  - タスク: db/seeds/*.sql（最小サンプル）、SeedService 実装、picocli ベース CLI の雛形（migrate/seed/sync）
  - 成果物: `scripts\seed.ps1` / `run-cli.bat` からシード投入可能
- Day 8: ApportionService（按分）単体テスト実装（1日）
  - タスク: Hamilton 法の実装と単体テスト（境界ケース含む）
  - 成果物: ApportionServiceTest が CI で成功
- Day 9: 統合テスト（Migration → Seed → 簡易クエリ） & CI 初版（.github/workflows/ci.yml）（1日）
  - タスク: Integration テストケース作成（ファイルベース SQLite を利用）、CI ワークフロー作成（mvn test）
  - 成果物: PR で CI が実行されテストが通る
- Day 10: ドキュメント整備・受け入れ確認（1日）
  - タスク: README の M1 手順更新、docs の該当箇所反映、M1 のデモ（ローカル動作確認）
  - 成果物: M1 完了報告用のチェックリストと簡易デモ手順

---

## 各タスクの責任、完了条件（テンプレ）
- 担当: TBD（例: Ken）
- 完了条件:
  - 自動化された手順で再現可能（migrate → seed → run basic query）
  - 単体/統合テストが CI で成功
  - docs に手順が記載されている

---

## 重要チェックポイント（中間レビュー）
- End of Day 3: MigrationRunner と接続が安定しているか（PRAGMA 適用含む）
- End of Day 5: 001_init.sql により基本スキーマが作成できるか
- End of Day 7: CLI で migrate/seed が動くか
- End of Day 9: CI が通り、主要ユースケースが再現できるか

---

## 受入基準（M1 完了の定義）
1. `mvn -DskipTests=false test` がローカル & CI で成功する
2. `scripts\migrate.ps1`（または run-cli migrate）で DB が作成される
3. `scripts\seed.ps1`（または run-cli seed）でサンプルデータが投入され、簡易クエリ結果が期待通りである
4. change_log がトリガで記録されることを確認済み
5. ApportionService の単体テストがパスしている
6. docs/M1_BATCH_SCHEDULE.md と README に初期手順が記載されている

---

## リスクと軽減策
- リスク: SQLite の並行性や PRAGMA 設定ミス → 軽減: SqlitePragmaConfigurer を早期実装、起動ログで検証
- リスク: マイグレーションの破壊的変更 → 軽減: スクリプトは冪等、schema_version を使った追跡
- リスク: テスト環境と開発環境差異 → 軽減: 統合テストはファイルベース SQLite を使用して本番に近づける

---

## 実行コマンド例（Windows）
- migrate（PowerShell）
  - .\scripts\migrate.ps1
- seed（PowerShell）
  - .\scripts\seed.ps1
- CLI（jar 実行想定）
  - run-cli.bat migrate
  - run-cli.bat seed
- テスト
  - mvn test

---

## 次の提案
- このスケジュールを GitHub Issues に分割してチケット化（私がドラフトを作れます）
- まず Day2 の `app-core` 雛形作成を開始しましょうか？

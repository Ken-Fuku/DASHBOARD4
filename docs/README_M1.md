# M1 操作手順（再現ガイド）

このドキュメントは M1 の成果物をローカルで再現する手順をまとめたものです。

## 前提
- JDK 21 と Maven が利用可能
- リポジトリを checkout してルートディレクトリで操作すること

## 推奨ワークフロー（短い）
1. クローン/更新
   - git clone ... または git pull
2. マイグレーション適用
   - PowerShell: .\scripts\migrate.ps1
   - あるいはテスト実行で自動適用: mvn -f app-core/pom.xml test
3. シード（サンプルデータ）
   - PowerShell: .\scripts\seed.ps1
   - 結果は `tools/seed_result.txt` に出力される
4. 統合テストの確認
   - mvn -f app-core/pom.xml test

## ファイル一覧（M1 で重要なファイル）
- db/migrations/001_init.sql — 初期スキーマとトリガ
- app-core/src/main/java/.../MigrationRunner.java — マイグレーション実行ロジック
- app-core/src/main/java/.../SqlitePragmaConfigurer.java — PRAGMA 設定
- scripts/migrate.ps1, scripts/seed.ps1 — PowerShell の migrate/seed ラッパ
- app-core/src/test/java/.../IntegrationFlowTest.java — マイグレーション→シード→検証の統合テスト
- .github/workflows/ci.yml — CI の初期ワークフロー

## よくある問題と対処
- sqlite3 がない: PowerShell の `scripts/seed.ps1` は sqlite3 CLI を呼びます。CI では JDBC ベースのテストを使うため必須ではありません。
- Windows ファイルパスの違い: PowerShell を使うケースは Windows 前提。Linux/Ubuntu の CI では `mvn test` を使って確認します。

## デモ手順（簡易）
- ローカル実行:
  - .\scripts\migrate.ps1
  - .\scripts\seed.ps1
  - type tools\seed_result.txt


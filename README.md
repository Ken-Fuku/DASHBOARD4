# DASHBOARD4 — M1 Snapshot

このリポジトリは M1（スケルトン + マイグレーション + サンプルデータ）を実装した Java + SQLite のプロジェクトです。

## 目的（短く）
- ローカル／CI 環境でマイグレーションを適用し、サンプルデータを投入して簡単なクエリで検証できること。

## 前提
- JDK 21, Maven がインストールされていること
- Windows の場合、`scripts/seed.ps1` を直接使うには `sqlite3` CLI が PATH にあること（統合テストは sqlite-jdbc を使うため CI では不要）

## クイックスタート（M1 手順）
# DASHBOARD4 — M1 Snapshot

このリポジトリは M1（スケルトン + マイグレーション + サンプルデータ）を実装した Java + SQLite のプロジェクトです。

## 目的（短く）
- ローカル／CI 環境でマイグレーションを適用し、サンプルデータを投入して簡単なクエリで検証できること。

## 前提
- JDK 21, Maven がインストールされていること
- Windows の場合、`scripts/seed.ps1` を直接使うには `sqlite3` CLI が PATH にあること（統合テストは sqlite-jdbc を使うため CI では不要）

## クイックスタート（M1 手順）
1. マイグレーション適用
   - PowerShell:
     - .\scripts\migrate.ps1
   - または Java 経由（テストで使用）:
     - mvn -f app-core/pom.xml test (統合テストが migration を含む)

2. サンプルデータ投入
   - PowerShell:
     - .\scripts\seed.ps1
   - CI/テスト実行時はテスト内で JDBC によるシードが行われます。

3. テスト実行

## 検証ポイント
- `tools/seed_result.txt` に visit_daily やマスタテーブルのカウントが記録される
- CI（.github/workflows/ci.yml）で `mvn -f app-core/pom.xml test` が実行される

## 問題が起きたら
- PowerShell スクリプトが sqlite3 を呼ぶ場合、Windows 環境で sqlite3 が PATH にないと失敗します。
- その場合は統合テストを使うか、`sqlite3` をインストールしてください。

## 連絡先
- コードベースの責任者: Ken

## Runtime configuration: automatic schema ALTER (safe-by-default)

インポート処理は、受信した change-log エントリを適用する際に既存テーブルへ欠けているカラムを自動で追加する（ALTER TABLE ADD COLUMN）オプションを持ちます。デフォルトではこの挙動は無効です。誤ったスキーマ変更を防ぐため、安全側がデフォルトになっています。
- Java システムプロパティ: `-DallowSchemaAlter=true`

注意事項:

- デフォルトは無効（false）です。明示的に有効化しない限り自動スキーマ変更は行われません。
- 開発環境や双方の変更元を管理できる限定された環境でのみ使用してください。プロダクション環境では推奨しません。
- 本プロジェクトでは本番運用向けに `db/migrations/` に明示的な SQL マイグレーションを用意することを推奨します。
- 有効化した場合、インポーターは受信データから型を推定して `ALTER TABLE` を実行します。生成されるスキーマ変更は必ずステージングで確認してください。

設定を行わず厳格なスキーマ運用を行いたい場合は、`ALLOW_SCHEMA_ALTER` を未設定または `true` 以外にしてください。

## Bench (import performance)

以下はローカルで計測した単一実行の結果です（Windows PowerShell 環境にて）。

- 生成した changelog: `data/bench-changelog.jsonl` — 2,134,535 バイト（10,000 エントリ）
- 生成された DB: `data/bench.db` — 1,490,944 バイト
- End-to-end（生成 + マイグレーション + インポート）：約 19.8 秒（ローカル測定）

再現メモ:

- 手動で Java を直接実行する場合、ランタイム依存 JAR が `app-core/target/dependency` に揃っていることを確認し、クラスパスとして `app-core/target/classes;app-core/target/dependency/*` を使用してください。
- SLF4J の SimpleLogger がファイル出力先として `app-core/target/logs/` を期待するため、マイグレーションやベンチ実行前に `app-core/target/logs` を作成してください（ベンチスクリプトはこのディレクトリ作成を行うようになっています）。

## Bench variants (複数バッチサイズでの測定)

付属スクリプト `scripts/bench/run_bench_variants.ps1` を使うと複数のバッチサイズでベンチを自動実行できます。出力は `bench_results/bench-results.csv` に追記されます。

例（PowerShell）:

```powershell
cd .\scripts\bench
.\run_bench_variants.ps1 -BatchSizes 50,100,200 -Count 10000
```

## CI: E2E オプトイン

リポジトリに手動トリガー可能な E2E ワークフローを追加しました: `.github/workflows/e2e-opt-in.yml`。
このワークフローは手動実行（workflow_dispatch）または外部トリガーで開始できます。外部サービスと連携する場合はリポジトリシークレット（UPLOAD_ENDPOINT、AUTH_TOKEN など）を設定し、ワークフローを拡張してください。



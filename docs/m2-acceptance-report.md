## M2 受入レポート（短報）

作成日: 2025-09-18

目的: M2（NDJSON ベースのインポート／エクスポート、tx_id の冪等化、source_node 保持、コンフリクト解決、apply の堅牢化、バックアップ/リストア、自動ベンチ）の完成判定を行う。

### 検証項目と判定

- 実装のビルド & テスト: PASS
  - コマンド: `cd app-core; mvn -DskipTests=false test -DtrimStackTrace=false`
  - 結果: テスト合計 26 件、失敗 0、エラー 0。BUILD SUCCESS（ローカル実行）
  - 抜粋:
    - `ChangeLogRepositoryTest`: Tests run: 2, Failures: 0, Errors: 0
    - `BackupRestoreTest`: Tests run: 2, Failures: 0, Errors: 0
    - `IntegrationFlowTest`: Tests run: 1, Failures: 0, Errors: 0

- ベンチ (簡易) – データ生成とインポート: PASS (ローカル計測)
  - ベンチ用ファイル: `data/bench-changelog.jsonl`（例サンプル確認済み）
  - ベンチ DB: `data/bench.db`（生成済み、サイズ・タイムスタンプ有）
  - 代表的な実行コマンド（PowerShell）:
    - `scripts/bench/generate_and_import.ps1 -Count 10000 -Out data/bench-changelog.jsonl -Db data/bench.db`
    - 内部で `RunCli import-changelog` を呼び、batchSize を変えて計測
  - ローカル結果例: 10k レコード生成 + マイグレーション + import の合計 ≈ 19.8 秒（環境依存）

### テストログ抜粋

ChangeLogRepositoryTest (要点)

```
Tests run: 2, Failures: 0, Errors: 0
  - testInsertAndList
  - testApplyCompositePkAndTypeConversion
```

BackupRestoreTest

```
Tests run: 2, Failures: 0, Errors: 0
```

IntegrationFlowTest

```
Tests run: 1, Failures: 0, Errors: 0
```

（詳細ログは `app-core/target/surefire-reports/` に配置されています）

### 合否判定

- 受入判定: 条件付き合格
  - 理由: 実装・テスト・ベンチの基本要件は満たしており、ローカルでの E2E（インポート／エクスポート・HTTP エンドポイント含む）も成功しています。
  - 条件: 本番運用に向けて下記の既知事項が対応されること（運用ポリシーとして明示するか、引き続き実装を完了する）。

### 既知の小課題（運用上の注意含む）

1. applyChangeToTable / applyChangeLogEntry の追加堅牢化が未完（優先度: 高）
   - 複合 PK の WHERE 句生成、NULL 値と型の境界条件、より広範な型マッピングについて更なるテストと補完が推奨されます。
   - 現状でも多くのケースをカバーする実装を入れてありますが、本番データの複雑さ次第で追加修正が必要です。

2. CI の E2E オプトイン設定（secrets 登録）が未完（優先度: 中）
   - `.github/workflows/e2e-opt-in.yml` は追加済み。リポジトリに E2E 用シークレット（例: `E2E_UPLOAD_ENDPOINT`, `E2E_AUTH_TOKEN`）を登録し、実行手順を README に追記してください。

3. ベンチスクリプトのファイルエンコーディングに注意
   - 以前、JSONL に BOM が入ると Jackson がパースエラーを出したため、スクリプトは UTF-8（BOMなし）で出力するように修正済み。

4. ログ出力先ディレクトリ
   - SLF4J SimpleLogger のログファイル出力先（`app-core/target/logs`）が存在しないと警告が出ます。実行前にディレクトリを作成するスクリプト側の対応を推奨。

### 再現手順（主要コマンド）

1) ビルド & テスト

```powershell
cd app-core; mvn -DskipTests=false test -DtrimStackTrace=false
```

2) ベンチ（例: 10k）

```powershell
.\scripts\bench\generate_and_import.ps1 -Count 10000 -Out data/bench-changelog.jsonl -Db data/bench.db
```

3) サーバーを立てて HTTP 経由で push/pull を試す

```powershell
cd app-core; java -cp target/classes;target/dependency/* com.kenfukuda.dashboard.cli.RunCli serve --port 8080
# 別ターミナルから curl で /sync/pull /sync/push を試す
```

（詳細は `docs/README_M2.md` と `docs/DB_SYNC_POLICY.md` を参照）

### 次の推奨アクション（優先度順）

1. applyChangeToTable の堅牢化と追加テスト（M2-014 / M2-012） — 高
   - 複合 PK、NULL、型ミスマッチのテストケースを追加してコードを補強します。推定時間: 数時間〜半日。

2. BackupRestoreTest に自動整合チェックを追加（M2-060） — 中
   - VACUUM/コピー→リストア→テーブル存在・行数サンプリング・レコードハッシュを自動検証するテストを追加します。推定 1–3 時間。

3. CI の E2E オプトイン完了（secrets 登録と README 追記）— 中

4. ベンチの自動化 & batchSize 決定（M2-100） — 低〜中
   - `run_bench_variants.ps1` で複数 batchSize を計測し、運用デフォルトを決定します。

---
短い結論: 実装のコアは完成しており、ローカルのテストとベンチで期待動作を確認できました。apply の最終的な堅牢化（複合 PK と型境界）を優先的に仕上げれば、本番受入に十分な状態になります。

作成: 開発チーム
# M2 Acceptance Report

作成日: 2025-09-18

本レポートは M2 項目の受入検証をまとめたものです。各 Issue について実装状況・証拠・受入判定（Acceptable / Needs Work）と推奨アクションを記載します。

## 実行環境とコマンド
- OS: Windows (PowerShell)
- JDK: 21
- 実行コマンド（テスト）:

```powershell
cd c:\Users\ken.fukuda\MyProgram\DASHBOARD4\app-core
mvn -DskipTests=false test -DtrimStackTrace=false
```

- ベンチ（生成→マイグレーション→インポート、10k）:

```powershell
cd scripts\bench
.\generate_and_import.ps1 -Count 10000 -OutFile ..\data\bench-changelog.jsonl -DbPath ..\data\bench.db -BatchSize 100
```

結果のサマリ:
- `mvn test`: BUILD SUCCESS（26 tests, Failures:0）
- ベンチ（10k End-to-end、ローカル計測）: 約 19.8 秒
- 生成 changelog: `data/bench-changelog.jsonl` — 2,134,535 bytes
- 生成 DB: `data/bench.db` — 1,490,944 bytes

---

## 各 Issue の受入判定

### M2-001: M2 設計仕様確定（DB_SYNC_POLICY / API スキーマ）
- 判定: Acceptable
- 証拠: `ExportChangeLogCommand`、`ServeCommand` 実装。`ServeHttpIntegrationTest` が /sync の基本動作を検証。`docs/DB_SYNC_POLICY.md` を追加。
- 残課題: OpenAPI 断片の必要に応じた作成。

### M2-010: ChangeLog Export コマンド実装
- 判定: Acceptable
- 証拠: `ExportChangeLogCommandTest`（エクスポート出力検証）。`ExportChangeLogCommand` が `__meta__` と `source_node` を出力。
- 残課題: ファイルレベル署名はオプション。

### M2-012: ChangeLog Import コマンド実装（idempotent）
- 判定: Acceptable (Partial)
- 証拠: `ImportChangeLogCommand` 実装（`runWithMainNodeId`/`runWithClientId`）、`ImportExportIntegrationTest` による検証。tx_id によるスキップロジック実装。
- 残課題: `applyChangeToTable` の完全化（複雑なスキーマ対応、NULL/型の堅牢化）。

### M2-014: ChangeLog 適用処理の堅牢化（applyChangeLogEntry 改良）
- 判定: Needs Work
- 証拠: tx 単位の savepoint/rollback、ベストエフォートな `applyChangeToTable` 実装あり。
- 残課題: リポジトリ層での API 化、複合 PK と型安全なバインドの実装（推奨アクションを参照）。

### M2-020: SyncStateRepository 実装（client_id ごとの last_lsn 管理）
- 判定: Acceptable
- 証拠: `SyncStateRepository` の upsert/getLastLsn、対応テスト。

### M2-030: ファイルベース Push/Pull CLI とスクリプト
- 判定: Acceptable
- 証拠: `scripts/` に push/pull 用スクリプトが存在し、手動/E2E で検証済み。
- 残課題: スクリプトのエラー処理と再試行ロジック強化（任意）。

### M2-040: REST Push/Pull API（簡易認証）
- 判定: Acceptable
- 証拠: `ServeCommand` の `/sync/pull` と `/sync/push`、`ServeHttpIntegrationTest`。
- 残課題: トークン管理の改善（将来）。

### M2-050: コンフリクト解消ロジック実装（LWW + マスタ優先）
- 判定: Acceptable
- 証拠: `ConflictResolver` テスト、E2E `PushPullConflictE2ETest`。
- 残課題: フィールド単位の差分ログ出力（将来）。

### M2-060: 監査／同期ログとバックアップ・復元スクリプト
- 判定: Acceptable (Partial)
- 証拠: Backup/Restore スクリプト、`BackupRestoreTest` 等のテストが存在。
- 残課題: リストア後の自動整合チェックを行うテストの追加を推奨。

### M2-070: 統合 E2E テスト作成（SyncE2ETest）
- 判定: Acceptable
- 証拠: `PushPullConflictE2ETest` 等追加済み、`mvn test` で E2E を含む全テスト成功。
- 残課題: 大規模データやパフォーマンスシナリオの追加は将来的に有用。

### M2-080: CI への E2E ステップ追加（オプトイン）
- 判定: Partial
- 証拠: `.github/workflows/e2e-opt-in.yml` を追加（手動トリガー可能）。
- 残課題: 実行環境（secrets）や夜間スケジュール／PR フローとの統合を設定する必要あり。

### M2-090: ドキュメント整備（README_M2.md / DB_SYNC_POLICY.md 完成）
- 判定: Acceptable
- 証拠: `docs/README_M2.md`（添付）および `docs/DB_SYNC_POLICY.md` を追加・更新。
- 残課題: ドキュメントの微調整（OpenAPI など）

### M2-100: 大量 change_log への対策（バッチ分割・チェックポイント）
- 判定: Acceptable
- 証拠: `ImportChangeLogCommand` にバッチロジックを追加、ベンチスクリプト `scripts/bench/*` によるローカル確認（10k 約 19.8 秒）。
- 残課題: 複数環境でのチューニング推奨。

### M2-110: 受入検証・デモ（Acceptance）
- 判定: Partial
- 証拠: 受入手順は文書化済み（`docs/README_M2.md`）だが、最終的な受入レポートは未作成（このファイルがその開始）。
- 残課題: 本レポートの補強（ログ抜粋の貼付、スクリーンショット、実行証跡ファイルの添付）

---

## 結論と次アクション（推奨）
短期（今すぐ）:
1. 受入レポートの完成（本ファイルを拡張） — テストログ抜粋、ベンチ CSV、結論を含める（推奨）
2. M2-014 (applyChangeLogEntry) の優先的改善 — 複合 PK と型バインドの堅牢化（高優先）

中期:
3. CI の E2E 完成（secrets 登録、夜間ジョブ追加）
4. リストア後の自動整合チェックの追加

長期:
5. OpenAPI の詳細化、フィールド差分ログの仕様化

---

次のアクションとして 1（受入レポート完成）を続けて実施してよいですか？

## 付録: 本セッションでのベンチ結果（生データ）

このファイルは本セッションで実行されたベンチ `scripts/bench/run_bench_variants.ps1` の出力をそのまま貼り付けたものです。
ファイル: `bench_results/bench-results.csv`

（注）同一ファイルへ複数回の実行ログが追記されているため、下記はファイル末尾に記録された最新の 4 行を抜粋しています。

```
batch=50,took_ms=30085.87,outfile=C:\Users\ken.fukuda\MyProgram\DASHBOARD4\data\bench-changelog.jsonl,db=C:\Users\ken.fukuda\MyProgram\DASHBOARD4\data\bench.db
batch=100,took_ms=13279.82,outfile=C:\Users\ken.fukuda\MyProgram\DASHBOARD4\data\bench-changelog.jsonl,db=C:\Users\ken.fukuda\MyProgram\DASHBOARD4\data\bench.db
batch=500,took_ms=10164.29,outfile=C:\Users\ken.fukuda\MyProgram\DASHBOARD4\data\bench-changelog.jsonl,db=C:\Users\ken.fukuda\MyProgram\DASHBOARD4\data\bench.db
batch=1000,took_ms=4632.69,outfile=C:\Users\ken.fukuda\MyProgram\DASHBOARD4\data\bench-changelog.jsonl,db=C:\Users\ken.fukuda\MyProgram\DASHBOARD4\data\bench.db
```

簡単な所見:
- batch=50 の実測が約 30.1 秒と大きく出ています。これは初回実行でのマイグレーションや DB 初期化（WAL/journal 設定、インデックス作成等）が影響した可能性が高いです。
- batch=100 は約 13.3 秒、batch=500 は約 10.2 秒、batch=1000 は約 4.6 秒でした。バッチサイズが大きくなるほど import の効率が改善していますが、I/O と初期オーバーヘッドの影響があります。

推奨: 再現性の高いベンチを得るために、ベンチ前に DB マイグレーションを事前に実行し、暖機運転（ウォームアップ）を 1 回入れてから計測することを推奨します。

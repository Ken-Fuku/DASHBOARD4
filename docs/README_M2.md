# M2 実装サマリ

このドキュメントは M2 の各 Issue に対して「設計・実装・テスト・TODO」を整理した目録です。

※ 各 Issue の実装状況はリポジトリ内のコードとテストを元に作成しています。


---

## M2-001: M2 設計仕様確定（DB_SYNC_POLICY / API スキーマ）

- 設計
  - JSON Lines フォーマット（ファイルヘッダ `__meta__` と各行の `lsn/tx_id/table_name/pk_json/op/payload/created_at/source_node` を採用）。
  - tx_id は idempotency のキー。lsn は増分抽出に使用。
  - REST API: `/sync/pull?from_lsn=N` (GET) は NDJSON を返し、`/sync/push` (POST) は NDJSON を受けて import をトリガーする。

- 実装
  - `ExportChangeLogCommand` が `__meta__` ヘッダと per-entry `source_node` を出力するように実装済み。
  - `ServeCommand` の HTTP ハンドラが `/sync/pull` と `/sync/push` を実装済み（簡易トークン認証あり）。

- テスト
  - `ServeHttpIntegrationTest` が /sync エンドポイントの基本動作を検証。

- TODO
  - OpenAPI 断片（docs/openapi/sync.yaml）への反映（必要なら詳細化）。


---

## M2-010: ChangeLog Export コマンド実装

- 設計
  - DB の `change_log` から指定 LSN より後のエントリを `ExportChangeLogCommand` で NDJSON 出力。
  - 出力時に `__meta__` を先頭行として付与し、`source_node` を明記する。

- 実装
  - `ExportChangeLogCommand.run(db, fromLsn, outFile, sourceNode)` を実装済み。per-entry に `source_node` を埋める。

- テスト
  - `ExportChangeLogCommandTest` がエクスポートの出力数・フォーマットを検証。

- TODO
  - file-level の署名／検証（必要なら）


---

## M2-012: ChangeLog Import コマンド実装（idempotent）

- 設計
  - インポートは NDJSON を読み、ヘッダの `tx_grouping=true` を期待。tx_id 毎に Savepoint を切り、失敗時にその tx のみロールバックする。
  - 既存 DB の tx_id の存在チェックで重複を無視（idempotency）。
  - import の最後に `sync_state` を同一トランザクション内または同一接続で upsert する。

- 実装
  - `ImportChangeLogCommand` に `runWithMainNodeId(..)` と `runWithClientId(..)` を実装済み。既存 tx_id を事前に読み出してスキップする処理あり。
  - インポート時に per-entry の `source_node` を `ChangeLogEntry` に格納し、ConflictResolver に渡すようになっています。
  - 追加: import 時に `applyChangeToTable` 補助を導入し（ベストエフォート）、payload をターゲットテーブルへ反映する処理を試みるようにしました（E2E テスト補助のため）。失敗しても change_log の挿入および sync_state 更新は行われます。

- テスト
  - `ImportExportIntegrationTest` が idempotency と sync_state 更新を検証しています。
  - E2E テストの Pull ケースで import の挙動を検証済み。

- TODO
  - `applyChangeToTable` の完全化（複雑なスキーママッピング、NULL/型対応、トランザクションの振る舞い改善）。


---

## M2-014: ChangeLog 適用処理の堅牢化（applyChangeLogEntry 改良）

- 設計
  - apply ロジックは tx_id と lsn による idempotency を担保し、例外が発生した場合は tx 単位でロールバックする。

- 実装
  - Import ロジック内で既存 tx の重複チェックと Savepoint を用いることで tx 単位ロールバックを実現。
  - `applyChangeToTable` はテスト支援のためにベストエフォート実装を追加したが、完全な applyChangeLogEntry 実装は継続課題。

- テスト
  - `ImportExportIntegrationTest` と E2E の一部が idempotency を検証。

- TODO
  - applyChangeLogEntry の API をリポジトリ層に移して再利用性を高める。
  - 型安全なマッピング、複雑キー対応、複合 PK への対応。


---

## M2-020: SyncStateRepository 実装（client_id ごとの last_lsn 管理）

- 設計
  - `sync_state` テーブルで client_id 毎の last_lsn を管理。Import 時に upsert する。

- 実装
  - `SyncStateRepository` に upsert/getLastLsn 実装あり。Import から呼び出して更新するフローを実装済み。

- テスト
  - `SyncStateRepositoryTest` と `SyncStateRepositoryUpsertTest` が基本操作を検証。
  - Integration test で import 後に sync_state が更新されることを確認。

- TODO
  - multi-node 環境での last_lsn マージ戦略（将来検討）。


---

## M2-030: ファイルベース Push/Pull CLI とスクリプト

- 設計
  - Scripts (`scripts/push_pull.ps1` 等) で local ファイルを媒介に export/import を行えるようにする。

- 実装
  - CLI の `export-changelog` と `import-changelog` を組み合わせたスクリプトを `scripts/` に用意済み（Windows PowerShell 用）。

- テスト
  - 手動スクリプトで動作確認済み。E2E テストで push/pull を呼び出すパターンを検証。

- TODO
  - スクリプトのエラー処理とログを強化（再試行ロジック等）。


---

## M2-040: REST Push/Pull API（簡易認証）

- 設計
  - Token ベースの簡易認証を用意し、/sync/push と /sync/pull を提供する軽量サーバを実装。

- 実装
  - `ServeCommand` で JDK の HttpServer を使い `/sync/pull` と `/sync/push` を実装済み。push ではアップロードを一時ファイルに保存し `ImportChangeLogCommand.runWithMainNodeId` を呼ぶ。

- テスト
  - `ServeHttpIntegrationTest` と E2E テストで /sync/push と /sync/pull を検証。

- TODO
  - トークン管理の改善（静的トークン→短期署名トークンや OAuth 連携など）


---

## M2-050: コンフリクト解消ロジック実装（LWW + マスタ優先）

- 設計
  - デフォルトは Last-Write-Wins（created_at 比較）。Company / Store 等のマスタテーブルは main の変更を優先するルールを追加。
  - provenance（`source_node`）を利用して incoming/from-main の判定を行う。

- 実装
  - `ConflictResolver` を実装し、既存レコードの `source_node` を参照するように改良（persisted provenance を優先的に使用）。
  - `ImportChangeLogCommand` は既存の最新エントリを取得し、`resolver.resolve(existing, incoming, incomingFromMain, existingFromMain)` を呼んで勝者を決定する。

- テスト
  - `ConflictResolverTest` のユニットテストがあり、E2E の `PushPullConflictE2ETest` で prefer-main の動作を確認。

- TODO
  - 競合時の詳細ログ（どのフィールドが差分か）を出す改善。
  - 競合解消ルールのポリシーを設定ファイル化（運用側でルールを切り替えられるようにする）。


---

## M2-060: 監査／同期ログとバックアップ・復元スクリプト

- 設計
  - 同期ごとの操作ログを出力し、バックアップ／復元スクリプトで DB のフルコピーを行う。

- 実装
  - スクリプトの雛形（backup/restore）と同期ログの出力方針を追加。

- テスト
  - 手動で backup/restore を試行。

- TODO
  - 自動テスト化（リストア後に整合性チェックを行うテスト）。


---

## M2-070: 統合 E2E テスト作成（SyncE2ETest）

- 設計
  - migration → seed → export → import の完全フローを自動化し、代表的なケース（再適用、競合）を検証する。

- 実装
  - E2E テスト `PushPullConflictE2ETest` を追加（本作業で拡張）。

- テスト
  - `mvn test` で E2E を含めた統合テストが動作することを確認済み。

- TODO
  - E2E のシナリオ追加（大規模データ、パフォーマンス計測用ケース）。


---

## M2-080: CI への E2E ステップ追加（オプトイン）

- 設計
  - PR 時は unit テストのみ、E2E はオプトイン（フラグ）で実行する形。heavy step は nightly に移行。

- 実装
  - CI 設定は未確定（要追加）。

- テスト
  - CI 上での動作確認は未実施。

- TODO
  - `.github/workflows/ci.yml` に opt-in E2E ステップを追加。


---

## M2-090: ドキュメント整備（README_M2.md / DB_SYNC_POLICY.md 完成）

- 設計
  - 運用手順、API 仕様、CLI 使い方を整理し README_M2 を作成。

- 実装
  - 本ファイル `docs/README_M2.md` を作成（このファイル）。

- テスト
  - -

- TODO
  - `docs/DB_SYNC_POLICY.md` の最終版を追加。


---

## M2-100: 大量 change_log への対策（バッチ分割・チェックポイント）

- 設計
  - 大量データに対してはバッチ分割とチェックポイント更新を導入する。

- 実装
  - 実装済み: `ImportChangeLogCommand` にバッチサイズでコミットを分割する処理を追加しました。
    - デフォルトバッチサイズは 100（システムプロパティ `import.batchSize` または環境変数 `IMPORT_BATCH_SIZE` で上書き可能）。
    - 各バッチ後に WAL のチェックポイント（PRAGMA wal_checkpoint）を呼び出し、ディスクへの確定を助けます。
    - tx_id の重複チェックによる idempotency と、各 tx 単位の savepoint/rollback を維持します。

- テスト
  - ベンチ用スクリプト `scripts/bench/generate_and_import.ps1` と E2E テストを用いて動作確認済み（ローカル測定: 10k レコードの End-to-end 約 19.8 秒）。

- TODO
  - バッチサイズとチェックポイント戦略のチューニング（複数環境での計測を推奨）。


---

## M2-110: 受入検証・デモ（Acceptance）

- 設計
  - README_M2 の手順に従って受入検証を行い、スクショやログを集めデモ資料を作成する。

- 実装
  - 準備中（手順文書は作成済み）。

- テスト
  - Acceptance チェックリスト実行が必要。

- TODO
  - 受入検証の実行とレポート作成。


---

# 付録: テスト実行のヒント

- ローカルでユニット／統合／E2E を実行する:

```powershell
cd c:\Users\ken.fukuda\MyProgram\DASHBOARD4\app-core
mvn test
```

- E2E を個別実行する場合は該当テストクラスだけ指定:

```powershell
mvn -Dtest=com.kenfukuda.dashboard.integration.PushPullConflictE2ETest test
```


# 完了・検証済み
- E2E テスト追加と `mvn test` による全テスト実行: 成功（BUILD SUCCESS）。


---

作成日: 2025-09-18

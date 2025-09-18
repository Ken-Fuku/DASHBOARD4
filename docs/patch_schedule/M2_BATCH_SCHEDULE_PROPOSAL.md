# M2 バッチスケジュール（提案）

目的: M1 の成果（スケルトン・マイグレーション・シード）を基に、同期機能の実稼働化・信頼性向上・運用性強化を実装する。
期間想定: 2 週間（10 営業日）推奨。リソースにより前後可能。

---

## スコープ（M2 の対象）
- ChangeLog のエクスポート/インポート（ファイルベース JSON lines）実装
- SyncState（クライアント別 last_lsn 管理）実装
- ローカル file-based Push/Pull CLI（PowerShell / bat スクリプト含む）
- REST ベースの簡易 Push/Pull API（認証トークン: 初期は簡易トークン）
- コンフリクト解消ポリシー（LWW, tx_id）と基本的な自動/手動運用手順
- applyChangeLogEntry の idempotent 実装強化
- 監査・同期ログ出力、バックアップ/復元スクリプト
- 統合 E2E テスト（ファイルベース SQLite を使用）
- CI 拡張（E2E を小規模で実行）

---

## 成果物（想定）
- docs/README_M2.md（手順・API 仕様・運用）
- docs/DB_SYNC_POLICY.md（ファイルフォーマット・tx_id・LSN 方針の追補）
- app-core 更新（ChangeLogExport/Import, SyncStateRepository, Sync CLI, REST API）
- scripts/push_pull.ps1 / run-cli 拡張
- tests/integration/SyncE2ETest.java（Migration→Seed→Push→Pull→整合性検証）
- .github/workflows/ci.yml の E2E ステップ追加（オプトイン実行）

---

## 受入基準（測定可能）
1. migrate → seed → export-change-log → import-change-log の自動化手順が成功すること（ローカル）
2. 同期後の DB 整合性テスト（代表ケース）がパスすること（E2E テスト）
3. applyChangeLogEntry は再適用による重複や破壊を起こさない（idempotent）
4. CLI による file-based push/pull が動作し、SyncState.last_lsn が更新されること
5. docs/README_M2.md と docs/DB_SYNC_POLICY.md が完成していること
6. CI 上で mvn test（＋E2E が有効な場合は小規模 E2E）が通ること

---

## 推奨スケジュール（10 営業日案）

Week 1
- Day 1: 設計・仕様確定（半日） + ドキュメント草案
  - 具体: JSON lines フォーマット、ファイルメタ、REST API スペック、コンフリクトポリシーを確定
  - 成果物: docs/DB_SYNC_POLICY.md（草案）・API スキーマ（OpenAPI 断片）
- Day 2–3: ChangeLog Export/Import 実装（2日）
  - Export: change_log を last_lsn ベースで JSON lines 出力（メタ含む）
  - Import: ファイルを読み込み、applyChangeLogEntry の idempotent 適用（tx_id/lsn 検査）
  - 成果物: ChangeLogExportCommand / ChangeLogImportCommand と単体テスト
- Day 4: SyncStateRepository 実装 + last_lsn 管理（1日）
  - 成果物: クライアント単位 last_lsn の読み書き API とテスト
- Day 5: CLI の file-based Push/Pull 実装（1日）
  - 成果物: run-cli push --file=... / run-cli pull --file=... と scripts/push_pull.ps1

Week 2
- Day 6: REST Push/Pull API サーバ（簡易認証）実装（1日）
  - 成果物: /sync/push /sync/pull エンドポイント（Token 認証）と簡易クライアント
- Day 7: コンフリクト解消ロジックと手順（1日）
  - 成果物: LWW ベース実装、手動解消 CLI 手順、docs 反映
- Day 8: 監査/同期ログ・バックアップスクリプト（1日）
  - 成果物: ログ出力ポリシー・scripts/backup.ps1 / restore.ps1
- Day 9: 統合 E2E テスト作成（1日）
  - 成果物: SyncE2ETest（複数ケース：単純適用・再適用・競合）
- Day 10: ドキュメント整備・CI 統合・受入検証（1日）
  - 成果物: docs/README_M2.md 完成、CI 更新、受入テスト通過

---

## 主要タスク詳細（短め）
- ChangeLog ファイル仕様
  - 形式: JSON Lines（UTF-8）、各行に change_log のエントリ
  - メタ: tx_id, source_node, exported_at, from_lsn, to_lsn
- Import の安全策
  - preflight: 重複 tx_id チェック、破壊的DDLの有無チェック
  - トランザクション: ファイル中の tx 単位でトランザクション処理
  - 失敗時: ロールバック + error レポート（ログ）
- SyncState
  - client_id（または node_id）で last_lsn を記録
  - Push 時は事前に pull を推奨（CLI に警告）
- コンフリクト方針
  - デフォルト: LSN 増分順で適用 → LWW（最後に適用された変更が優先）
  - マスタ系（Company/Store）は main 優先ルールを適用
  - 競合検出時は tombstone と payload 比較で自動/手動分岐

---

## テスト・CI ポリシー
- 単体: Export/Import ロジック、applyChangeLogEntry の idempotency、SyncState
- 統合: ファイルベース E2E（実 DB ファイルを用いた小スケール）
- CI: オプトインで E2E 実行（重い場合は nightly）
- テスト出力: app-core/target/logs に E2E ログを残す

---

## リスクと軽減策（要点）
- 大量 change_log の適用時間 → バッチ分割、チェックポイント（last_lsn 更新）
- 途中失敗のロールバック難 → tx_id 単位の確実なトランザクション管理
- セキュリティ（REST） → 初期はファイルベース優先、REST は TLS + トークンへ段階移行
- 不整合リスク → E2E テストケースを早期に追加

---

## 次のアクション（選択してください）
- A: この案でファイルを作成して保存してください（docs/patch_schedule/M2_BATCH_SCHEDULE_PROPOSAL.md を作成）  
- B: 上記案をベースに GitHub Issues のタスク群（タイトル・説明・受入条件）をドラフト作成してください  
- C: Day2（ChangeLog Export/Import 実装）の具体手順とコード雛形を出してください

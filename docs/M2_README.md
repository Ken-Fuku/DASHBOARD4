# M2 実装 README

このファイルは M2 の実装サマリ（短縮版）と、ローカルでの検証手順をまとめます。

目的
- M2 作業範囲: Export/Import（NDJSON）、ConflictResolver（LWW + prefer-main）、provenance（source_node）永続化、/sync HTTP API、E2E テスト、バックアップ/復元自動化の初期実装。

主要コンポーネント
- CLI
  - `ExportChangeLogCommand`, `ImportChangeLogCommand`, `ServeCommand`（picocli）
- Sync API
  - `/sync/pull?from_lsn=N` (GET) - NDJSON を返す
  - `/sync/push` (POST) - NDJSON を受けて import を実行
- DB
  - SQLite、`change_log` に `source_node TEXT` を追加
- Conflict Resolution
  - `ConflictResolver`（LWW デフォルト、company/store 等は main を優先）
- Backup
  - `BackupManager` + `scripts/backup.ps1` / `scripts/restore.ps1`

簡単な検証手順（ローカル）

1) 全テスト実行

```powershell
cd c:\Users\ken.fukuda\MyProgram\DASHBOARD4\app-core
mvn test
```

2) E2E（単一テスト）

```powershell
mvn -Dtest=com.kenfukuda.dashboard.integration.PushPullConflictE2ETest test
```

3) バックアップ/復元テスト（単一実行）

```powershell
mvn -Dtest=com.kenfukuda.dashboard.infra.BackupRestoreTest test
```

運用上の注意
- `apply` ロジックは現状ベストエフォート実装です。型変換や複合 PK を要するケースは追加の改善が必要です。
- トークン認証は簡易実装です。運用環境では短期署名トークン等の導入を検討してください。

今後の作業候補
- applyChangeLogEntry の堅牢化（型安全化、複合キー対応）
- バックアップの運用化（ローテーション、外部ストレージ保存、暗号化）
- CI に E2E オプトインワークフローを追加

作成日: 2025-09-18

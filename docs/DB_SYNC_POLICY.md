# DB_SYNC_POLICY

この文書は change-log ベース同期に関する運用ポリシーを定義します（競合解消、スキーマ進化、バッチ運用、受入基準）。

## 目的
- 競合解消ルールを明確化し、運用での一貫性を担保する
- スキーマ進化（ALTER）の取り扱いと責任を定義する
- 受入検証用のチェックリストを提供する

## 用語
- main: 中央集約側（master）ノード。運用上のソース・オブ・トゥルースと見なす。
- client: ローカル／分散ノード。main と同期（push/pull）を行う。
- provenance / source_node: 各 change_log エントリに格納される発信元ノード識別子。
- tx_id: トランザクション単位の一意キー（idempotency を保証するために使用）。

## 基本概念
- change_log に LSN（全体単調増加の番号）を付与。ログは実テーブルと同じ DB に保持。
- sync_state にクライアントごとの last_lsn を保持し、差分適用の再開点とする。
- 変更はトリガで記録（op: I/U/D、payload は JSON）。tx_id は UUID ベース。

## change_log 最低スキーマ
- lsn INTEGER PRIMARY KEY AUTOINCREMENT
- tx_id TEXT
- table_name TEXT
- pk_json TEXT
- op TEXT ('I'|'U'|'D')
- payload TEXT (JSON)
- tombstone INTEGER (0/1)
- created_at TEXT (ISO8601)
- source_node TEXT (provenance)

## コンフリクト解決

- 基本方針: Last-Write-Wins (LWW)
	- デフォルトは `created_at` の新しい方を勝者とする。
	- `created_at` は可能なら UTC / ISO-8601 形式で保存することを推奨。

- provenance（`source_node`）を使用したルール
	- 各 change_log エントリに `source_node`（発信ノード ID）が保存されています。これを用いて incoming が main 由来かどうかを判定できます。
	- `ConflictResolver` は `existingFromMain` / `incomingFromMain` を考慮してテーブルごとの `prefer-main` ルールを適用できます（例: マスタテーブル）。

- マスタ優先ルール (prefer-main)
	- 業務上重要なマスタテーブル（company, store など）は main の変更を優先する設定が可能です。

- 監査とログ
	- 競合解決の決定は監査ログに記録し、どのフィールドが差分であったかの簡易ログ出力を残すことを推奨します（実装は将来追加予定）。

## idempotency
- `tx_id` は重複適用を防ぐために使用
	- インポーターは適用前に既存の tx_id をチェックし、既に適用済みの tx_id はスキップする
	- tx 単位で savepoint を切り、失敗時はその tx のみロールバック（他の tx は継続）

## スキーマ進化ポリシー（ALLOW_SCHEMA_ALTER）
- デフォルト動作: 自動 ALTER は無効
	- 理由: 誤ったスキーマ変更によりデータ破壊や整合性欠損が起きるリスクを最小化するため

- allowSchemaAlter を有効にする場合
	- `-DallowSchemaAlter=true` を明示的に指定して起動する
	- 運用上のルール:
		- ステージング環境での十分な検証を必須とする
		- ALTER で追加されるカラムは NULL 許容とし、既存データに対して安全であること
		- 型推定はベストエフォート: 明示的な手動マイグレーションが推奨されるケースもある

- 変更記録
	- allowSchemaAlter による ALTER は監査ログ／手動レビューの対象とする

## バッチ処理とパフォーマンス

- 実装とデフォルト
	- `ImportChangeLogCommand` はバッチサイズでコミットを分割する実装になっています（デフォルト: 100）。
	- バッチサイズはシステムプロパティ `import.batchSize` または環境変数 `IMPORT_BATCH_SIZE` で上書きできます。
	- 各バッチ後に PRAGMA wal_checkpoint を呼び出してディスク確定を促します。

- 推奨バッチサイズ
	- 小規模環境: 100～500
	- 中規模: 500～2,000
	- 大量データ取り込み時は事前にベンチを行いチューニングしてください

注: 一括バルク適用（ATTACH/DETACH 等）は高速だが複雑さと安全性のトレードオフがあるため運用で慎重に採用すること。

## エクスポート形式（ファイルベース）

- JSON Lines（NDJSON）を採用。先頭行に `__meta__` を付与して `client_id` / `source_node` 等を含めます。
- 改ざん検出: ファイルレベルでハッシュ（SHA256）を付与することを推奨します。

## セキュリティ・伝送
- オフライン: ファイルの受け渡し（USB等）を想定。ファイル署名/ハッシュ推奨
- ネットワーク: REST over TLS（HTTP API）を後段で実装。認証はトークンまたは短期署名トークンを推奨

## 受入検証チェックリスト

### 機能面
- Export/Import が `__meta__` ヘッダを出力/解釈している
- `tx_id` による idempotency が機能している（同一 tx を複数回適用しても二重適用されない）
- `ConflictResolver` の LWW / `prefer-main` 動作が想定通り
- Import 成功時に `sync_state` が更新される

### 運用面
- `allowSchemaAlter` はデフォルト無効であることを確認
- 自動 ALTER を使う場合、ステージングで ALTER のログとデータ影響を確認
- バックアップ（VACUUM INTO / ファイルコピー）とリストア手順が文書化されている

### パフォーマンス
- ベンチ結果（10k など）を再現し、デフォルト batchSize で許容できること

## 運用フロー（簡易）
- main からクライアントへの同期
	- main: `export-changelog --from-lsn=N` → ファイルまたは HTTP で配布
	- client: `import-changelog --in file.jsonl` → import
- client から main への同期は対称的に push を使う

## 今後の改善候補
- フィールド単位の差分ログを出し、競合時にどのカラムが差分かを判定する仕組み
- ルールセットを設定ファイル化して運用側で切り替え可能にする

---

作成日: 2025-09-18

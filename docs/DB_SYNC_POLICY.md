# DB_SYNC_POLICY (原案)

目的: LSN と change_log による差分同期の仕様、tx_id・再開ポリシーを定義する。

## 基本概念
- change_log に LSN（全体単調増加の番号）を付与。ログは実テーブルと同じ DB に保持。
- sync_state にクライアントごとの last_lsn を保持し、差分適用の再開点とする。
- 変更はトリガで記録（op: I/U/D、payload は JSON）。tx_id は UUID ベース。

## change_log スキーマ（最低限）
- lsn INTEGER PRIMARY KEY AUTOINCREMENT
- tx_id TEXT
- table_name TEXT
- pk_json TEXT
- op TEXT ('I'|'U'|'D')
- payload TEXT (JSON)
- tombstone INTEGER (0/1)
- created_at TEXT (ISO8601)

## tx_id とトランザクション
- tx_id はトランザクション単位で発行（UUID v4 推奨）
- トランザクション内の複数行変更は同一 tx_id を使用
- 送受信時は tx_id をキーに原子適用/ロールバック判断を行う

## Push / Pull フロー（MVP はファイルベース）
- Pull: メイン側で last_lsn > client.last_lsn の change_log を抽出→エクスポート（JSON Lines）→クライアントがインポートして apply
- Push: クライアントがエクスポートした change_log をメインが受け取り、検証後 apply（apply は idempotent）
- 先に Pull を行い整合を取る（クライアントは push 前に必ず pull）

## 再開性とバッチ適用
- バッチはトランザクション単位で一括適用。各バッチの終端で sync_state.last_lsn を更新
- 適用失敗時はロールバックし、原因をログに出力。同じバッチは再度安全に適用できる（idempotent）

## コンフリクト解決
- 基本方針: メイン優先（Main wins）
- LWW（Last-Write-Wins）を基本。タイムスタンプ基準は tx_id 生成元のクロック（UTC）で比較
- マスタ（業務マスタ）については常にメインを優先して上書き
- 衝突がビジネス上致命的な場合は manual merge を要求するため、変更履歴と監査ログを残す

## 主キー戦略
- PK は原則 INTEGER (ROWID)
- 業務キーは UNIQUE 制約で同一性判定（例: store_code)
- 必要時に id_map（client_temp_id → main_id）を使用して解決

## バッチサイズとパフォーマンス
- 初期推奨: 1,000～10,000 行/バッチ（運用で調整）
- バルク適用は ATTACH/DETACH や一時 TRUNCATE を検討（安全性優先）

## エクスポート形式（ファイルベース）
- JSON Lines（1 行 = 1 change_log エントリ）
- メタ情報ファイル: {source_db, from_lsn, to_lsn, tx_count, exported_at}
- 検証: ハッシュ（SHA256）付きで改ざん検出を行う

## セキュリティ・伝送
- オフライン: ファイルの受け渡し（USB等）を想定。ファイル署名/ハッシュ推奨
- ネットワーク: REST over TLS（HTTP API）を後段で実装。認証は相互認証またはトークン

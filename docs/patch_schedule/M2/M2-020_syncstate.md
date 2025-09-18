# M2-020: SyncStateRepository 実装（client_id ごとの last_lsn 管理）

説明:
- sync_state テーブルの CRUD と API（getLastLsn/setLastLsn）を実装。CLI/Import と連携する。

受入条件:
- client_id 単位で last_lsn を読み書きできること
- Import/Export フローで正しく更新されることを確認する統合テスト

ラベル: backend, infra, test
想定工数: 0.5 day

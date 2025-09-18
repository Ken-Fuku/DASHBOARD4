# M2-014: ChangeLog 適用処理の堅牢化（applyChangeLogEntry 改良）

説明:
- applyChangeLogEntry の idempotency、tx_id/lsn 検査、エラーハンドリング、ログ出力を改善する。

受入条件:
- 同一 tx_id の再適用で副作用が発生しないこと
- 例外発生時は tx 単位でロールバックされること
- 単体テストで idempotency を検証

ラベル: backend, infra, test
想定工数: 1 day

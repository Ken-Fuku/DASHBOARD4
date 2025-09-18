# M2-012: ChangeLog Import コマンド実装（idempotent）

説明:
- JSON Lines ファイルを読み込み、tx 単位でトランザクション処理し applyChangeLogEntry を呼ぶ import CLI 実装。重複 tx_id を吸収する。

受入条件:
- `run-cli import-changelog --file=...` が動作し、import 後に last_lsn 更新が可能であること
- 再実行で DB が重複しないこと（idempotent）
- 単体テストあり（失敗時ロールバック検証含む）

ラベル: backend, cli, test
想定工数: 1.5 days

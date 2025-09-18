# M2-010: ChangeLog Export コマンド実装

説明:
- change_log を client_id または from_lsn ベースで JSON Lines にエクスポートする CLI 実装。

受入条件:
- `run-cli export-changelog --from-lsn=N --to=file` が動作すること
- 出力例の JSON Lines が docs で定義したスキーマに準拠していること
- 単体テストあり

ラベル: backend, cli, test
想定工数: 1 day

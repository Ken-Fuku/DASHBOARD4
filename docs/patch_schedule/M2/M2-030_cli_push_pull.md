# M2-030: ファイルベース Push/Pull CLI とスクリプト

説明:
- run-cli push/pull（ファイル指定）と Windows 用 scripts/push_pull.ps1 を追加。push は export→ファイル作成、pull は file→import のラッパー。

受入条件:
- `scripts\push_pull.ps1` を実行し、片端→もう片端への同期フローが再現できること
- CLI の exit code とログが正しいこと

ラベル: cli, scripts, infra
想定工数: 0.5 day

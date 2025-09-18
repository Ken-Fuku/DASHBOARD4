# M2-060: 監査／同期ログとバックアップ・復元スクリプト

説明:
- 同期操作の監査ログ出力ルールを実装。scripts/backup.ps1, scripts/restore.ps1 を追加（VACUUM INTO 等を含む）。

受入条件:
- 同期ごとにログが app-core/target/logs に出力されること
- backup/restore スクリプトで DB のフルバックアップと復元ができること

ラベル: ops, scripts, docs
想定工数: 0.5 day

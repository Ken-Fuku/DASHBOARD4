# 06: CLI と CSV エクスポータ実装

目的

CLI コマンド経由でレポートを実行し、CSV ファイルにエクスポートできるようにする。

受け入れ基準

- `ReportC1Command` が `--yyyymm` `--company` などのパラメータで実行できること。
- CLI 実行で `tools/report_c1_YYYYMMDD_HHMMSS.csv` に CSV を出力すること（UTF-8+BOM、RFC4180）。
- 大きな出力（何万行）でもストリームで書き出せること（メモリに全件を保持しない）。

タスク

- (A) CLI 引数パーサの定義（picocli を使用する想定）。
- (B) CSV エクスポートユーティリティの実装（BufferedWriter、BOM、エスケープ）。
- (C) 実行ログを `app-core/target/logs/report_query.log` に残す。
- (D) 出力先パスとファイル名ルールを文書化。

担当

未設定

見積もり

0.5 日
# D6 完了報告

このドキュメントは D6 の完了条件と、実行手順および証跡の場所を示します。

## 完了条件
- `scripts\migrate.ps1` を実行して DB 作成 → `scripts\seed.ps1` でサンプル投入
- サンプル投入後に簡易クエリ（例: `SELECT count(*) FROM visit_daily`）で期待件数を確認

## 実行手順（今回の作業）
1. `scripts\migrate.ps1` を実行（内部で `app-core/run_migrate.ps1` を呼び出します）
2. `scripts\seed.ps1` を実行（`sqlite3` を利用）
   - `scripts\seed.sql` によるマスタ系の挿入
   - `scripts\seed.ps1` のループで `visit_daily` を 100 件挿入
   - 検証クエリの結果を `tools/seed_result.txt` に保存
   - 実行ログは `app-core/target/logs/seed.log` に保存

## 証跡（今回生成されたファイル）
- `tools/seed_result.txt` — 検証クエリ結果（visit_daily=100, company=5, store=5, budget_monthly=5, budget_factors=5）
- `app-core/target/logs/seed.log` — シード実行ログ（開始時刻、実行中のメッセージなど）

## 注意点 / 今後の改善案
- 初回実行時の重複挿入や外部キーエラーがログに残る可能性があるため、`scripts/seed.ps1` は実行前にログを上書きするように変更済み。
- CI で再現する場合は `sqlite3` の有無を確認し、インストール手順を CI 定義に加える必要があります。

## 実行例
```powershell
# 作業ディレクトリで実行
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\migrate.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\seed.ps1
```

---
作業を担当した自動化エージェントによる実行（ログ・結果はリポジトリにコミット済み）

## D6 実行完了の記録

完了日時: 2025-09-16T14:44:51+09:00

コミット: 8c9e803 (HEAD)

証跡ファイル:

- `tools/seed_result.txt` — 検証結果（visit_daily=100, company=5, store=5, budget_monthly=5, budget_factors=5）
- `app-core/target/logs/seed.log` — 実行ログ（開始/完了のタイムスタンプを含む）

備考:

- 初回実行時に重複/外部キ―制約でのエラーが発生した痕跡がログに残っていましたが、`scripts/seed.sql` を冪等化し `scripts/seed.ps1` を修正して最新ログはクリーンとなっています。
- このドキュメントを D6 の完了証跡として扱ってください。

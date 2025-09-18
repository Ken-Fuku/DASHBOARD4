M4 Performance bench and indexing guidance

目的
- C1〜C4 レポートクエリのパフォーマンス改善に向けた簡易ベンチ手順と、SQLite 向けの推奨インデックスを記載します。

推奨インデックス（SQLite）
- visit_daily テーブル
  - CREATE INDEX IF NOT EXISTS idx_visit_daily_visit_date ON visit_daily(visit_date);
  - CREATE INDEX IF NOT EXISTS idx_visit_daily_store_id ON visit_daily(store_id);
  - 複合インデックス（keyset 用）:
    - CREATE INDEX IF NOT EXISTS idx_visit_daily_date_store ON visit_daily(visit_date DESC, store_id ASC);
- store テーブル
  - CREATE INDEX IF NOT EXISTS idx_store_id ON store(id);
- budget_monthly（もし JOIN が重い場合）
  - CREATE INDEX IF NOT EXISTS idx_budget_monthly_store_yearmonth ON budget_monthly(store_id, year_month);

注意点
- SQLite はインデックス作成が I/O を伴うため、本番データサイズが大きい場合はメンテナンスウィンドウで実行してください。
- 単純なインデックスで十分改善する場合が多いですが、クエリパターンに合わせてプロファイリングを行ってください。

簡単なベンチ手順（Windows PowerShell）
1) テスト DB をコピーして、ベンチ用 DB を作る
```powershell
cp .\data\app.db .\data\bench.db
```

2) インデックスを追加（sqlite3 CLI がある前提）
```powershell
sqlite3 .\data\bench.db "CREATE INDEX IF NOT EXISTS idx_visit_daily_date_store ON visit_daily(visit_date DESC, store_id ASC);"
```

3) 実行時間測定（単発クエリを 10 回実行して平均を取る）
```powershell
$times = @()
for ($i=0; $i -lt 10; $i++) {
  $t0 = Get-Date
  sqlite3 .\data\bench.db "-- ここに c1.sql の実行クエリ（プレースホルダを具体値に展開）"
  $t1 = Get-Date
  $times += ($t1 - $t0).TotalMilliseconds
}
$times | Measure-Object -Average -Minimum -Maximum
```

4) 比較
- インデックス追加前後で上記測定を繰り返し、平均・最小・最大を比較してください。

5) 追加調査
- EXPLAIN QUERY PLAN を使って、クエリがインデックスを利用しているか確認してください。例:
```powershell
sqlite3 .\data\bench.db "EXPLAIN QUERY PLAN SELECT ... FROM visit_daily ..."
```

次のステップ（任意）
- 本格的にベンチを自動化するなら、Java のベンチフレームワーク（JMH）でレポート用クエリを実装し、相対的な改善を測定するのが良いです。
- データ量（行数）を増やしたテストセットを用意し、インデックスの効果を確認してください。

備考
- このドキュメントは簡易ガイドです。必要なら私が実際のベンチスクリプト（PowerShell）や JMH サンプルを作成して、結果レポートまで用意します。
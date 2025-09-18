API: 日次配賦取得

エンドポイント
- GET /api/v1/allocations/daily

クエリパラメータ
- year (必須): 年 (例: 2025)
- month (必須): 月 (1-12)
- companyAmount (任意): 会社共通の月次金額（数値）
- stores (任意): 店舗ごとの月次金額を指定する文字列（例: "A:600|B:300"）

レスポンス例
```
{
  "year": 2025,
  "month": 9,
  "daily": [
    {"date": "2025-09-01", "companyAmount": 10.00, "stores": [{"storeId":"A","amount":20.00}, {"storeId":"B","amount":10.00}]},
    ...
  ],
  "totalMonthly": 1200.0
}
```

使い方（例）
- 会社共通 300、店舗 A 600、B 300 を 2025 年 9 月に按分する:
  GET /api/v1/allocations/daily?year=2025&month=9&companyAmount=300&stores=A:600|B:300

備考
- 端数処理: 初期実装は "last-day"（各日の小数点以下を切捨て、最後の日に残差を加算）
- 将来的に丸め方法を変更できるよう、クエリや設定で戦略を指定できるように拡張予定

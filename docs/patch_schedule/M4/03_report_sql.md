# 03: レポート SQL 断片作成 (C1〜C4)

目的

C1〜C4 の集計クエリを SQL 断片として作成し、`app-core/src/main/resources/sql/report/` に配置できる形に整備する。

受け入れ基準

- C1〜C4 それぞれの主要 SQL（SELECT/GROUP BY/ORDER BY/WHERE）が定義されていること。
- SQL はパラメータ化されており、PreparedStatement に渡せる形であること（例：:company_id, :yyyymm, :limit, :cursor）。
- サンプルデータで SQL を実行して期待値のサンプル結果を README に含める。

タスク

- (A) C1 の SQL（会社→エリア→店舗ごとの集計）を作成。
- (B) C2/C3/C4 の SQL を作成。
- (C) SQL 断片を `resources/sql/report/` にファイルとして用意する。
- (D) パフォーマンス上の注記（必要なインデックス候補）を記載する。

担当

未設定

見積もり

1.0 日
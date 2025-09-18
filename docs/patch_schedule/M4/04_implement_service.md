# 04: ReportQueryService 実装（C1 優先）

目的

`application.usecase.ReportQueryService` に C1 を優先して実装し、DTO を返すサービスを作る。

受け入れ基準

- `ReportQueryService` の C1 メソッドが存在し、`C1Request` を受け取り `PagedResult<C1Row>` を返す。
- SQL の PreparedStatement を安全に実行し、キーセットページングで `nextCursor` が返ること。
- 例外処理が適切に行われ、ログにクエリ時間が記録されること。

タスク

- (A) Service のメソッドシグネチャ設計。
- (B) SQL 読み込みユーティリティを用意（resources/sql/report/c1.sql を読み込む）。
- (C) DB 実行と Row → DTO マッピングを実装。
- (D) nextCursor の生成ロジックを実装。
- (E) ログ出力（実行時間）を追加。

担当

未設定

見積もり

1.5 日
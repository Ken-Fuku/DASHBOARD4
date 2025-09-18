# 07: 単体テスト作成（ReportQueryService）

目的

ReportQueryService および Pagination ユーティリティの単体テストを作成し、ロジックの回帰を防ぐ。

受け入れ基準

- 主要パス（C1 の正常系）をカバーするユニットテストがあること。
- 境界値テスト（0件、limit 未指定、最後ページ）を含むこと。
- テストは `mvn test` で実行可能で成功すること。

タスク

- (A) ReportQueryServiceTest の作成（モック DB またはインメモリ SQLite を使用）。
- (B) PaginationTest の作成。
- (C) CI 実行時にテストが通ることを確認。

担当

未設定

見積もり

1.0 日
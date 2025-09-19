# 05: キーセットページング実装／Pagination ユーティリティ

目的

大規模データ向けにキーセットページングを実装するユーティリティを用意し、`ReportQueryService` から再利用できるようにする。

受け入れ基準

- `Pagination` ユーティリティ（キーフィールドのエンコード/デコード、limit の検証）が実装され、単体テストで検証されていること。
- nextCursor のフォーマットが明文化されていること（Base64 等でエンコードされた JSON: {lastValues: [...], sortOrder: "asc"} 推奨）。

タスク

- (A) nextCursor フォーマット決定（Base64(JSON) 推奨）。
- (B) Pagination クラスの API 設計（encode/decode, applyToPreparedStatement）。
- (C) 単体テストを作成。

担当

未設定

見積もり

0.5 日
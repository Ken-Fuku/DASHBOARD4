# 02: DTO と API 契約定義

目的

C1〜C4 の Request/Response DTO と共通ページング DTO を設計し、Java クラス名とフィールドを定義する。

受け入れ基準

- `C1Request`, `C1Row` 等の DTO スペックが決まっていること（フィールド、型、説明）。
- 共通ページング DTO（`PagedResult<T>` または `ReportPage<T>`）のスキーマが定義されていること。
- JSON シリアライズのサンプル（例: items + totalCount + nextCursor + generatedAt）を含むこと。

タスク

- (A) C1Request / C1Row のフィールド定義。
- (B) PagedResult<T> の仕様（nextCursor フォーマット、null の扱い）。
- (C) DTO を格納する Java パッケージ構成を決める（`application.dto.report`）。
- (D) クラススケルトンを `app-core/src/main/java/...` に追加する（PR 用の下書き）。

担当

未設定

見積もり

0.5 日
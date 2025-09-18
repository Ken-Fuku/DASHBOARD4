M4: レポート計算API (C1〜C4)

このフォルダには M4（レポート計算API）の作業を GitHub Issue 形式で整理した Markdown ファイルを格納します。

ファイル一覧:

1. 01_define_requirements.md — 要件定義／受け入れ基準確定
2. 02_define_dtos.md — DTO と API 契約定義
3. 03_report_sql.md — レポート SQL 断片作成 (C1〜C4)
4. 04_implement_service.md — ReportQueryService 実装（C1 優先）
5. 05_pagination.md — キーセットページング実装／Pagination ユーティリティ
6. 06_cli_csv.md — CLI と CSV エクスポータ実装
7. 07_unit_tests.md — 単体テスト作成（ReportQueryService）
8. 08_integration_tests.md — 統合テスト作成（Migration→Seed→Report）
9. 09_ci_docs.md — CI とドキュメント更新
10. 10_bench.md — 性能ベンチと最適化（目安）

進め方:
- 各ファイルは Issue テンプレート風に「目的」「受け入れ基準」「タスク」「担当（未設定）」「見積もり」を含みます。
- まず `01_define_requirements.md` を in-progress として作業を開始しました。
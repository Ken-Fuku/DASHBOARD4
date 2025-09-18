# Implementation Report — M3: 按分エンジン + 日次API

実施日: 2025-09-18
担当: （未割当）

概要
- 按分ロジックを `AllocationService` として実装。API は `AllocationController`（Spring MVC 想定）で簡易ハンドラを追加。
- 単体テストを `AllocationServiceTest` に追加。

実装方針
- 内部はセント（整数）で計算し、端数は初期実装で "last-day"（最終日調整）方式とした。
- 優先順位は "company -> stores" を想定し、`allocateCompanyThenStores` メソッドで会社共通分と店舗分を日次でそれぞれ生成し、日次合成を返す。

テスト
- 29/30/31 日のサンプルケース、合計整合の単体テストを追加。

注意点
- プロジェクトに Spring / JUnit の依存が無い場合、pom.xml に依存追加が必要（本実装はサンプルとして追加）。

今後の作業
1. pom.xml に必要な依存（spring-boot-starter-web、junit-jupiter など）を追加
2. 実アプリケーションに組み込み、ステージ環境で結合テストを実施
3. PR を作成してレビューを依頼

# TEST_POLICY (原案)

目的: ユニット・統合・負荷・回帰テストの方針と CI 運用を定義する。

## テスト階層
- ユニットテスト
  - 対象: ドメインロジック（按分ロジック、日付計算、ユーティリティ）
  - モック: Repositories/外部 I/O をモック化
  - 実行: mvn test（CI）
- 統合テスト
  - 対象: MigrationRunner → Seed → Repository 操作
  - 環境: ファイルベースの SQLite（実ファイルを作成）
  - 例: SyncServiceIT, ReportQueryServiceIT
- エンドツーエンド / IT
  - 実 DB ファイルを用い CLI を通して主要フローを検証
- 負荷テスト
  - 目的: C1 < 1s / C4 < 3s（店舗600×365 規模）を目安にボトルネック抽出
  - ツール: JMH（計算処理）または小型 load script（集計API）
- 回帰テスト
  - マイグレーション前後でデータ差分比較を自動化（CI）

## テストデータとシード
- 最小サンプル: Company1 / Store2 / Visit 14日 / Budget 1件 / Users3
- シードは db/seeds/*.sql と SeedService で再現可能にする

## CI
- .github/workflows/ci.yml
  - プルリク: mvn -DskipTests=false test
  - main/tag: mvn package → アーティファクト作成
- カバレッジ基準: 初期は必須ではないが、重要ロジック（按分）は 80% 目安

## テスト実装規約
- テスト名: {ClassName}Test / {ClassName}IT
- テストデータはテストごとに独立作成・クリーンアップ
- 外部リソース依存は最小化（スタブ/モックで代替）

## マイグレーション回帰テスト
- MigrationRunner の各スクリプトに対し「適用前 DB -> 適用後 DB の期待差分」を CI でチェック
- schema_version とスクリプト SHA256 を検証

## ローカル実行コマンド（Windows）
- mvn test
- scripts\migrate.ps1 で DB 初期化
- scripts\seed.ps1 でサンプル投入

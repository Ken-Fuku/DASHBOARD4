# ARCHITECTURE AND FOLDER (原案)

目的: 層構造と命名規約を明確化し、将来の拡張（モジュール分割 / UI追加）を容易にする。

## 層構造（高レベル）
- domain: エンティティ・値オブジェクト・ドメインサービス（ビジネスルール）
- application: ユースケース（UseCase/Service）、DTO、入力検証、トランザクション境界
- infrastructure: DB 接続・リポジトリ実装・マイグレーション・外部連携
- interface: CLI（初期）→ 将来 UI(JavaFX)

## Java パッケージ命名（例）
- com.kenfukuda.dashboard.domain...
- com.kenfukuda.dashboard.application...
- com.kenfukuda.dashboard.infrastructure...
- com.kenfukuda.dashboard.cli...

## ディレクトリ構成（抜粋）
- /app-core/src/main/java/...
- /db/migrations/*.sql (順序付き、冪等)
- /docs/*.md (設計・運用ドキュメント)
- /scripts/*.ps1 / *.sh (起動・マイグレーション・シード)
# ARCHITECTURE AND FOLDER (原案)

目的: 層構造と命名規約を明確化し、将来の拡張（モジュール分割 / UI追加）を容易にする。

## 層構造（高レベル）
- domain: エンティティ・値オブジェクト・ドメインサービス（ビジネスルール）
- application: ユースケース（UseCase/Service）、DTO、入力検証、トランザクション境界
- infrastructure: DB 接続・リポジトリ実装・マイグレーション・外部連携（ファイル/HTTP）
- interface: CLI（初期）・後に ui-javafx を設置。CLI は application を呼ぶ薄い層とする。

## Java パッケージ命名（例）
- com.kenfukuda.dashboard.domain...
- com.kenfukuda.dashboard.application...
- com.kenfukuda.dashboard.infrastructure...
- com.kenfukuda.dashboard.cli...

クラス名ルール
- エンティティ: PascalCase（例: Company, Store）
- リポジトリインタフェース: XxxRepository（例: StoreRepository）
- リポジトリ実装: XxxSqliteRepository（例: StoreSqliteRepository）
- サービス: XxxService（例: ApportionService）
- ユースケース: XxxUseCase / XxxService（application 層で統一）

## ディレクトリ構成（リファレンス）
- /app-core/src/main/java/...（ソース）
- /app-core/src/test/java/...（テスト）
- /db/migrations/*.sql（順序付き、冪等）
- /db/seeds/*.sql（サンプルデータ）
- /docs/*.md（設計・運用ドキュメント）
- /scripts/*.ps1 / *.sh（起動・マイグレーション・シード）

## リソース配置
- SQL 断片、大型クエリ: /app-core/src/main/resources/sql/
- 設定: application.properties（環境差分は config/*.env）
- ログ設定: logback.xml（resources）

## テスト/CI
- ユニット: src/test/java — 依存はモック化
- 統合/IT: ファイルベースの SQLite を使用（実ファイルを作成）
- CI: .github/workflows/ci.yml で mvn test を実行

## ドキュメント
- ドキュメントは docs/ 以下に機能別に分離（DB_RULES_SQLITE.md, DB_SYNC_POLICY.md 等）
- 変更は PR として追跡、主要変更は CHANGELOG に記録

# TEST_POLICY (原案)

目的: ユニット・統合・負荷・回帰テストの方針と CI 運用を定義する。

## テスト階層
- ユニットテスト: ドメインロジック（按分ロジック、日付計算、権限）
- 統合テスト: MigrationRunner → Seed → Repository 操作（実ファイルベースの SQLite）
- エンドツーエンド / IT: CLI を使った実行フローの検証
- 負荷テスト: JMH または簡易 load script

## テストデータとシード
- 最小サンプル: Company1 / Store2 / Visit 14日 / Budget 1件 / Users 3
- シードは `db/seeds/*.sql` と `SeedService` で再現可能にする

## CI
- .github/workflows/ci.yml は `mvn -DskipTests=false test` を実行
- メトリクス: 初期は 80% のカバレッジ目安（特に按分/同期ロジック）

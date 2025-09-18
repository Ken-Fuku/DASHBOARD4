# 09: CI とドキュメント更新

目的

CI で `mvn test` を実行し、レポート周りのテストが自動で回るように設定する。README に CLI 実行手順を記載する。

受け入れ基準

- `.github/workflows/ci.yml` が `mvn -DskipTests=false test` を実行すること。
- README に `report c1` の実行例と生成される CSV の場所が記載されていること。
- CI の成果物にテストログ（surefire reports）を添付するオプションを検討/追加する。

タスク

- (A) CI ワークフローの確認と必要な修正。
- (B) README のサンプルコマンドを追加。
- (C) CI 成果物（テストログ、生成CSV）のアップロード検討。

担当

未設定

見積もり

0.5 日
PR: Add opt-in secret wiring for E2E workflows

概要

この PR は E2E 実行時にのみリポジトリシークレット（UPLOAD_ENDPOINT, AUTH_TOKEN）を注入できるように、既存のワークフローを安全に拡張するものです。日常の push/pr 実行ではシークレットは注入されず、手動の workflow_dispatch 実行時に `use_secrets: 'true'` を指定した場合のみ注入されます。

変更点

- .github/workflows/e2e-opt-in.yml
  - workflow_dispatch に input `use_secrets` を追加
  - 手動実行時に `use_secrets == 'true'` のときにのみ `UPLOAD_ENDPOINT` / `AUTH_TOKEN` をステップの環境変数として注入

- .github/workflows/ci.yml
  - workflow_dispatch に input `use_secrets` を追加
  - e2e ジョブの E2E 実行ステップで条件付きに環境変数を注入

理由

- デフォルトの CI 実行（push / pr）ではシークレットが注入されないため安全
- 手動オプトイン時にのみ外部との実環境接続情報を渡せる

必要なリポジトリシークレット

- UPLOAD_ENDPOINT (例: https://example.com/sync/push)
- AUTH_TOKEN (ベアラートークン等)

ワークフローの使い方（例）

1) GitHub UI の Actions -> 該当ワークフロー -> Run workflow を使う場合
   - Input `use_secrets` を 'true' に設定して Run

2) GitHub API / gh CLI を使う例

# gh CLI を使って workflow_dispatch を実行する例
# gh workflow run e2e-opt-in.yml -f use_secrets=true

マージ手順（ローカル）

1. ローカルで新しいブランチを作成
   git checkout -b feature/ci-e2e-opt-in
2. 変更をコミットして push
   git add .github/workflows/e2e-opt-in.yml .github/workflows/ci.yml
   git commit -m "ci: opt-in secret wiring for E2E workflows"
   git push --set-upstream origin feature/ci-e2e-opt-in
3. GitHub 上で PR を作成し、レビュワーに確認してもらう

注意点

- secrets の登録はリポジトリ管理者で行ってください。
- E2E 実行時のログにトークンが吐かれないことを確認してください（スクリプト側でも `AUTH_TOKEN` を echo しない等）。

作成者: 自動 PR ドラフト

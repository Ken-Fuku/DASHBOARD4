# PROJECT_RULES (原案)

目的: コーディング規約、ブランチ運用、コミットルール、アーティファクト管理を定義する。

## ブランチ戦略
- trunk-based development
  - main: 常にデプロイ/検証可能（保護）
  - feat/*: 小さく短命（例: feat/sync-push-cli）
  - fix/*: バグ修正
  - release/*: 必要時のみ
- マージ: Squash or Merge commit はチームルールで統一

## コミットメッセージ
- Conventional Commits を必須
  - feat: , fix: , refactor: , test: , docs:
- PR テンプレートに「目的/設計要点/テスト観点」を必須項目として追加

## コード規約（Java）
- 基本: Google Java Style / 会社規約に沿う
- パッケージ命名: com.kenfukuda.dashboard...
- ログ: SLF4J + logback で統一（ログレベルは環境依存で調整）
- 例外処理: checked/unchecked の使い分けを明示、必ず意味あるメッセージを付与

## ファイル/アーティファクト管理
- .gitignore: *.db, /dist/**, *.zip, *.jar を除外
- 大容量: LFS は原則不使用。必要時は審査の上導入
- リリースビルド: GitHub Releases に JAR/ZIP を添付（CI 自動化）

## スキーマ / バージョニング
- アプリ SemVer と schema_version を連動
- 破壊的変更はマイグレーションで対応、手動DDLは禁止

## PR レビュー要件
- ビルド成功
- テスト成功（ユニット + 影響範囲の IT）
- 変更点の説明（PR テンプレ）
- 影響範囲のドキュメント更新（必要箇所）

## CI ルール
- PR: mvn -DskipTests=false test を実行
- タグ: パッケージング → Releases への自動アップロード
- セキュリティスキャン/静的解析は段階的に導入

## 運用ルール（簡易）
- config/*.env で環境差分管理（APP_ENV=dev|stg|prd）
- PROD DB は読み取り専用のバックアップポリシーを別途定義
- 重要な運用手順は docs/ 以下に手順化（マイグレーション適用、バックアップ復元手順等）

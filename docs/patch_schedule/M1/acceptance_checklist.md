# M1 受入チェックリスト

作成日: 2025-09-16

このチェックリストは M1 の受入基準を満たしているかを記録します。

## チェック項目

1. mvn -DskipTests=false test がローカルで成功する
   - ステータス: 合格
   - 証跡: ローカル実行で `BUILD SUCCESS`（9 tests, 0 failures）

2. mvn -DskipTests=false test が CI で成功する
   - ステータス: 要確認
   - 証跡: `.github/workflows/ci.yml` を追加して push 済み。Actions の実行結果を確認すること。

3. `scripts/migrate.ps1` で DB が作成される
   - ステータス: 合格
   - 証跡: `app-core/run_migrate.ps1` と `scripts/migrate.ps1` の存在、統合テストで migration を実行している

4. `scripts/seed.ps1` でサンプルデータが投入される
   - ステータス: 合格（ローカル）
   - 証跡: `tools/seed_result.txt` に 100 / 5 / 5 / 5 / 5 の出力（visit_daily, company, store, budget_monthly, budget_factors）

5. change_log がトリガで記録される
   - ステータス: 合格
   - 証跡: `db/migrations/001_init.sql` にトリガ定義あり。ChangeLogRepository のテストが存在し、通過済み。

6. ApportionService の単体テストがパスしている
   - ステータス: 合格
   - 証跡: `ApportionServiceTest` が存在しローカルでパス

7. docs に M1 手順が記載されている
   - ステータス: 合格
   - 証跡: `README.md`, `docs/README_M1.md` を作成済み

## 総合判定
- ローカル検証: 合格
- CI 上での最終確認: 未完了（Actions 実行結果の確認が必要）

---

次の推奨アクション:
- GitHub Actions の実行結果確認（成功なら M1 完了扱いにできる）
- 必要なら CI ワークフローを拡張してテスト成果物（JUnit XML）をアップロード


# M2-070: 統合 E2E テスト作成（SyncE2ETest）

説明:
- Migration → Seed → Export → Import → 検証 のフローを自動化する統合テスト群（単純適用、再適用、競合ケース）。

受入条件:
- `tests/integration/SyncE2ETest.java` が存在し、ローカルで mvn test により成功すること
- テスト結果のログが app-core/target/logs に出力されること

ラベル: test, integration, ci
想定工数: 1.5 days

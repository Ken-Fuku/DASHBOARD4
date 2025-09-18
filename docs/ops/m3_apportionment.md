# M3: 按分エンジン（月→日配賦）ドキュメント

目的
- 月次予算を日次に配賦する按分エンジンを実装する。要件は月合計不変、端数は最大剰余法（Hamilton法）で処理すること。

要件（優先順）
1. 合計不変: 月合計値の合計が常に一致すること（整数化後も一致）
2. 端数処理: Hamilton 法（最大剰余法）を採用
3. 係数優先: 店舗個別係数があれば優先、なければ会社共通係数を使用
4. 係数ゼロ/負: 仕様で禁止するか、ゼロは 0 割当、負はエラー（実装時に明文化）
5. OPEN/CLOSE月: 月単位扱い（按分しない）
6. 性能目標: 店舗600 × 365 の月次処理が実用範囲（ベンチで確認）

実装箇所（候補）
- com.kenfukuda.dashboard.domain.service.ApportionService
- 単体テスト: app-core/src/test/java/com/kenfukuda/dashboard/domain/service/ApportionServiceTest.java
- CLI呼び出し: SeedCommand などのデータ投入フローでの実行ポイント

アルゴリズム（概略）
1. 係数の正規化: 全係数の合計で割合を算出
2. 初期割当: floor(total × ratio)
3. 余り算出: total - sum(initial)
4. 剰余順位付け: 各要素の fractional part（total × ratio の小数部）でソートし、上位から 1 を割当（余りが尽きるまで）

テストチェックリスト
- 合計不変チェック（整数化後も等しい）
- 端数多数ケース（多くの同率剰余）
- 係数に 0 を含むケース
- 全係数ゼロ（仕様での扱い）
- 単一日・少数日ケース（1日/2日の月）
- 境界値（非常に大きな月合計）

TODO（最小PR）
1. ApportionService に Hamilton 法を実装
2. ApportionServiceTest に合計不変・端数テスト追加
3. docs の参照（本ファイル）へのリンクを README に追加

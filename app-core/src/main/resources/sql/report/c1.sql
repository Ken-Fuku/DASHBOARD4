-- C1: 会社/店舗/日別の来店客数集計
-- パラメータ: :company_id (nullable), :store_id (nullable), :yyyymm_start (YYYY-MM-DD), :yyyymm_end (YYYY-MM-DD), :limit, :cursor_values (optional)

SELECT
  c.id AS company_id,
  c.name AS company_name,
  s.id AS store_id,
  s.store_code AS store_code,
  s.name AS store_name,
  v.visit_date AS date,
  v.visitors AS visitors,
  b.daily_budget AS budget
FROM visit_daily v
JOIN store s ON s.id = v.store_id
JOIN company c ON c.id = s.company_id
LEFT JOIN (
  -- 仮想的な日次予算テーブル（BudgetMonthly を日割りするロジックを実装すること）
  SELECT bm.store_id, bm.year_month, (bm.budget_amount / 30) AS daily_budget
  FROM budget_monthly bm
) b ON b.store_id = v.store_id AND b.year_month = strftime('%Y-%m', v.visit_date)
WHERE (:company_id IS NULL OR c.id = :company_id)
  AND (:store_id IS NULL OR s.id = :store_id)
  AND v.visit_date BETWEEN :yyyymm_start AND :yyyymm_end
-- ソートは日付 DESC, store_id ASC（キーセットページング用）
ORDER BY v.visit_date DESC, s.id ASC
LIMIT :limit;

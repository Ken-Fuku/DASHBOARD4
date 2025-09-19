-- C4: 大規模一覧（店舗×日 or 店舗×月）の上位/フィルター表示用テンプレ
-- TODO: 実装詳細を確定する
SELECT
  s.id AS store_id,
  s.store_code,
  s.name AS store_name,
  v.visit_date AS date,
  v.visitors
FROM visit_daily v
JOIN store s ON s.id = v.store_id
WHERE (:company_id IS NULL OR s.company_id = :company_id)
  AND v.visit_date BETWEEN :start_date AND :end_date
ORDER BY v.visit_date DESC, s.id ASC
LIMIT :limit;

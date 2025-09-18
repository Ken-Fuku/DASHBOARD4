-- C2: 会社/ブロック/エリア別の集計テンプレ
-- TODO: 実装詳細を確定する
SELECT
  c.id AS company_id,
  c.name AS company_name,
  s.block AS block,
  s.area AS area,
  SUM(v.visitors) AS visitors_sum
FROM visit_daily v
JOIN store s ON s.id = v.store_id
JOIN company c ON c.id = s.company_id
WHERE (:company_id IS NULL OR c.id = :company_id)
  AND v.visit_date BETWEEN :start_date AND :end_date
GROUP BY c.id, s.block, s.area
ORDER BY visitors_sum DESC
LIMIT :limit;

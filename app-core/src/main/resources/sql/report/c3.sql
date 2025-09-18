-- C3: 月次/会社別の比較（前年同月含む）
-- TODO: 実装詳細を確定する
SELECT
  c.id AS company_id,
  c.name AS company_name,
  strftime('%Y-%m', v.visit_date) AS yyyymm,
  SUM(v.visitors) AS visitors_sum
FROM visit_daily v
JOIN store s ON s.id = v.store_id
JOIN company c ON c.id = s.company_id
WHERE (:company_id IS NULL OR c.id = :company_id)
  AND v.visit_date BETWEEN :start_date AND :end_date
GROUP BY c.id, yyyymm
ORDER BY yyyymm DESC
LIMIT :limit;

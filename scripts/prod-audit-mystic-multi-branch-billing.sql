-- Mystic Wellness: customers billed at more than one branch (legacy cross-branch tagging).
-- Run on prod RDS (read-only audit).

\echo '=== 1) Customers with invoices at 2+ branches ==='
SELECT
  c.id AS customer_id,
  c.name,
  COALESCE(c.phone, '') AS phone,
  c.visit_pass_id,
  hb.code AS customer_home_branch,
  COUNT(DISTINCT i.branch_id) AS invoice_branch_count,
  STRING_AGG(DISTINCT ib.code, ', ' ORDER BY ib.code) AS billed_branch_codes,
  COUNT(i.id) AS invoice_count,
  MIN(i.created_at) AS first_invoice_at,
  MAX(i.created_at) AS last_invoice_at
FROM customers c
JOIN tenants t ON t.id = c.tenant_id
JOIN branches hb ON hb.id = c.branch_id
JOIN invoices i ON i.customer_id = c.id
JOIN branches ib ON ib.id = i.branch_id
WHERE lower(t.slug) = 'mystic-wellness'
GROUP BY c.id, c.name, c.phone, c.visit_pass_id, hb.code
HAVING COUNT(DISTINCT i.branch_id) > 1
ORDER BY invoice_branch_count DESC, c.name;

\echo '=== 2) Bookings where booking.branch_id != customer.branch_id ==='
SELECT
  c.id AS customer_id,
  c.name,
  hb.code AS customer_home_branch,
  bb.code AS booking_branch,
  COUNT(*) AS booking_count
FROM customers c
JOIN tenants t ON t.id = c.tenant_id
JOIN branches hb ON hb.id = c.branch_id
JOIN bookings b ON b.customer_id = c.id
JOIN branches bb ON bb.id = b.branch_id
WHERE lower(t.slug) = 'mystic-wellness'
  AND b.branch_id IS DISTINCT FROM c.branch_id
GROUP BY c.id, c.name, hb.code, bb.code
ORDER BY booking_count DESC, c.name;

\echo '=== 3) Summary counts ==='
SELECT
  (SELECT COUNT(*) FROM customers c JOIN tenants t ON t.id = c.tenant_id WHERE lower(t.slug) = 'mystic-wellness') AS total_customers,
  (SELECT COUNT(*) FROM (
     SELECT c.id
     FROM customers c
     JOIN tenants t ON t.id = c.tenant_id
     JOIN invoices i ON i.customer_id = c.id
     WHERE lower(t.slug) = 'mystic-wellness'
     GROUP BY c.id
     HAVING COUNT(DISTINCT i.branch_id) > 1
   ) x) AS customers_multi_branch_invoices,
  (SELECT COUNT(DISTINCT c.id)
   FROM customers c
   JOIN tenants t ON t.id = c.tenant_id
   JOIN bookings b ON b.customer_id = c.id
   WHERE lower(t.slug) = 'mystic-wellness'
     AND b.branch_id IS DISTINCT FROM c.branch_id) AS customers_with_cross_branch_bookings;

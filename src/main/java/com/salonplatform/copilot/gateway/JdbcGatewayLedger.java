package com.salonplatform.copilot.gateway;

import com.salonplatform.aigateway.ledger.AuditEvent;
import com.salonplatform.aigateway.ledger.GatewayLedger;
import com.salonplatform.aigateway.ledger.UsageSnapshot;
import com.salonplatform.aigateway.model.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The gateway's durable ledger: limits survive restarts and hold across several servers, and the same rows give
 * AntraHQ a view of who is using the assistant and what it costs.
 */
@Component
@RequiredArgsConstructor
public class JdbcGatewayLedger implements GatewayLedger {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public void record(AuditEvent e) {
        jdbc.update("""
                INSERT INTO ai_gateway_events (id, created_at, kind, channel, tenant_id, user_id, decision, reason, categories,
                    rule_ids, provider, model, tokens_in, tokens_out, cost_inr, latency_ms, input_chars)
                VALUES (:id, :at, :kind, :channel, :tenant, :user, :decision, :reason, :categories,
                    :rules, :provider, :model, :tin, :tout, :cost, :latency, :chars)""",
                new MapSqlParameterSource()
                        .addValue("id", e.id())
                        .addValue("at", Timestamp.from(e.at()))
                        .addValue("kind", e.kind())
                        .addValue("channel", e.channel())
                        .addValue("tenant", e.tenantId())
                        .addValue("user", e.userId())
                        .addValue("decision", e.decision())
                        .addValue("reason", truncate(e.reason(), 80))
                        .addValue("categories", e.categories().stream().map(Category::name).collect(Collectors.joining(",")))
                        .addValue("rules", truncate(e.ruleIds(), 200))
                        .addValue("provider", e.provider())
                        .addValue("model", truncate(e.model(), 100))
                        .addValue("tin", e.tokensIn())
                        .addValue("tout", e.tokensOut())
                        .addValue("cost", e.costInr() == null ? BigDecimal.ZERO : e.costInr())
                        .addValue("latency", e.latencyMillis())
                        .addValue("chars", e.inputChars()));
    }

    @Override
    public UsageSnapshot snapshot(String tenantId, String userId, Instant dayStart, Instant monthStart, Instant violationsSince) {
        return jdbc.queryForObject("""
                SELECT
                  COUNT(*) FILTER (WHERE kind = 'REQUEST' AND user_id = :user AND created_at >= :day)               AS user_requests,
                  COUNT(*) FILTER (WHERE kind = 'REQUEST' AND created_at >= :day)                                    AS tenant_requests,
                  COALESCE(SUM(cost_inr) FILTER (WHERE created_at >= :day), 0)                                       AS spend_day,
                  COALESCE(SUM(cost_inr), 0)                                                                         AS spend_month,
                  COUNT(*) FILTER (WHERE kind = 'BLOCK' AND reason <> 'EMPTY' AND user_id = :user AND created_at >= :viol) AS violations,
                  MAX(created_at) FILTER (WHERE kind = 'BLOCK' AND reason <> 'EMPTY' AND user_id = :user AND created_at >= :viol) AS last_violation
                FROM ai_gateway_events
                WHERE tenant_id = :tenant AND created_at >= :month""",
                new MapSqlParameterSource()
                        .addValue("tenant", tenantId)
                        .addValue("user", userId)
                        .addValue("day", Timestamp.from(dayStart))
                        .addValue("month", Timestamp.from(monthStart))
                        .addValue("viol", Timestamp.from(violationsSince)),
                (rs, i) -> new UsageSnapshot(rs.getInt("user_requests"), rs.getInt("tenant_requests"), rs.getBigDecimal("spend_day"),
                        rs.getBigDecimal("spend_month"), rs.getInt("violations"),
                        rs.getTimestamp("last_violation") == null ? null : rs.getTimestamp("last_violation").toInstant()));
    }

    /** Per business over the last {@code days}: questions, refusals, tokens and spend, for platform admins. */
    public List<Map<String, Object>> summaryByTenant(int days) {
        return jdbc.queryForList("""
                SELECT e.tenant_id                                                            AS "tenantId",
                       COALESCE(MAX(t.name), e.tenant_id)                                      AS "tenant",
                       COUNT(*) FILTER (WHERE e.kind = 'REQUEST')                              AS "questions",
                       COUNT(*) FILTER (WHERE e.kind = 'BLOCK')                                AS "refused",
                       COUNT(*) FILTER (WHERE e.kind = 'LIMIT')                                AS "limitHits",
                       COUNT(*) FILTER (WHERE e.kind = 'ANSWERED')                             AS "answeredFree",
                       COUNT(*) FILTER (WHERE e.kind = 'REDACT')                               AS "redactions",
                       COALESCE(SUM(e.tokens_in), 0)                                           AS "tokensIn",
                       COALESCE(SUM(e.tokens_out), 0)                                          AS "tokensOut",
                       ROUND(COALESCE(SUM(e.cost_inr), 0), 2)                                  AS "spendInr"
                FROM ai_gateway_events e
                LEFT JOIN tenants t ON t.id::text = e.tenant_id
                WHERE e.created_at >= now() - (:days * interval '1 day')
                GROUP BY e.tenant_id
                ORDER BY SUM(e.cost_inr) DESC NULLS LAST""", new MapSqlParameterSource("days", days));
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}

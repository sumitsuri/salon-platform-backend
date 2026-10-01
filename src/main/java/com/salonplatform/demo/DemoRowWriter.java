package com.salonplatform.demo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Buffered multi-table JDBC batch inserts with a pause between batches. Tables flush in the order
 * they were first registered, so parents registered first are written before their children.
 */
@Slf4j
class DemoRowWriter {

    private record Buffer(String sql, int columns, List<Object[]> rows) {}

    private final JdbcTemplate jdbcTemplate;
    private final int batchSize;
    private final long pauseMillis;
    private final Map<String, Buffer> buffers = new LinkedHashMap<>();
    private final Map<String, Integer> written = new LinkedHashMap<>();

    DemoRowWriter(JdbcTemplate jdbcTemplate, int batchSize, long pauseMillis) {
        this.jdbcTemplate = jdbcTemplate;
        this.batchSize = Math.max(50, batchSize);
        this.pauseMillis = Math.max(0, pauseMillis);
    }

    /** Declare a table's insert columns; call once per table, parents before children. */
    void table(String table, String... columns) {
        String placeholders = String.join(",", java.util.Collections.nCopies(columns.length, "?"));
        String sql = "INSERT INTO " + table + " (" + String.join(",", columns) + ") VALUES (" + placeholders + ")";
        buffers.put(table, new Buffer(sql, columns.length, new ArrayList<>()));
    }

    void add(String table, Object... values) {
        Buffer buffer = buffers.get(table);
        if (buffer == null) {
            throw new IllegalStateException("Undeclared demo table " + table);
        }
        if (values.length != buffer.columns()) {
            throw new IllegalArgumentException(table + " expects " + buffer.columns() + " values, got " + values.length);
        }
        Object[] row = new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            row[i] = toJdbc(values[i]);
        }
        buffer.rows().add(row);
        if (buffer.rows().size() >= batchSize) {
            flushAll();
        }
    }

    /** Flush every table in registration order (keeps parent rows ahead of children). */
    void flushAll() {
        for (Map.Entry<String, Buffer> entry : buffers.entrySet()) {
            Buffer buffer = entry.getValue();
            if (buffer.rows().isEmpty()) {
                continue;
            }
            jdbcTemplate.batchUpdate(buffer.sql(), buffer.rows());
            written.merge(entry.getKey(), buffer.rows().size(), Integer::sum);
            buffer.rows().clear();
            pause();
        }
    }

    Map<String, Integer> writtenCounts() {
        return written;
    }

    void pause() {
        if (pauseMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(pauseMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Demo data job interrupted", e);
        }
    }

    private static Object toJdbc(Object value) {
        if (value instanceof Instant instant) {
            return Timestamp.from(instant);
        }
        if (value instanceof LocalDate date) {
            return Date.valueOf(date);
        }
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        return value;
    }
}

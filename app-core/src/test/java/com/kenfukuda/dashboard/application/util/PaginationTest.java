package com.kenfukuda.dashboard.application.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

public class PaginationTest {

    @Test
    public void encodeDecodeCursor() {
        Object[] last = new Object[] {"2025-01-31", 123L};
        String cursor = Pagination.encodeCursor(last, "desc");
        assertNotNull(cursor);

        Map<String, Object> decoded = Pagination.decodeCursor(cursor);
        assertNotNull(decoded);
        assertEquals("desc", decoded.get("sort"));
        Object[] values = ((java.util.ArrayList<?>) decoded.get("lastValues")).toArray();
        assertEquals("2025-01-31", values[0]);
        // JSON mapping will treat numbers as Integer/Long depending; compare string form
        assertEquals("123", String.valueOf(values[1]));
    }
}

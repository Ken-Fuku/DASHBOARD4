package com.kenfukuda.dashboard.application.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.LinkedHashMap;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * シンプルなキーセットページングカーソルのエンコード/デコード
 * Cursor は Base64(JSON) で表現する（例: {"lastValues":["2025-01-31",123],"sort":"desc"}）
 */
public class Pagination {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String encodeCursor(Object[] lastValues, String sort) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lastValues", lastValues);
            m.put("sort", sort);
            byte[] json = MAPPER.writeValueAsBytes(m);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> decodeCursor(String cursor) {
        try {
            if (cursor == null) return null;
            byte[] json = Base64.getUrlDecoder().decode(cursor);
            return MAPPER.readValue(json, Map.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid cursor", ex);
        }
    }
}

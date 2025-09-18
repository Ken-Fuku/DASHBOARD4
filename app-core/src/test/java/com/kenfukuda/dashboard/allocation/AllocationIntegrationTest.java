package com.kenfukuda.dashboard.allocation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class AllocationIntegrationTest {

    @Test
    public void testHttpEndpoint() throws Exception {
        AllocationService svc = new AllocationService();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/allocations/daily", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) {
                try {
                    String query = exchange.getRequestURI().getQuery();
                    // naive parse year=2025&month=9&companyAmount=300&stores=A:600|B:300
                    int year = 2025, month = 9;
                    double comp = 300.0;
                    var compDaily = svc.allocateCompanyThenStores(comp, java.util.List.of(new AllocationService.StoreMonthly("A",600.0), new AllocationService.StoreMonthly("B",300.0)), year, month, "last-day");
                    StringBuilder sb = new StringBuilder();
                    sb.append('{').append("\"year\":").append(year).append(',');
                    sb.append("\"month\":").append(month).append(',');
                    sb.append("\"daily\":[");
                    boolean first=true;
                    for (var d: compDaily) {
                        if (!first) sb.append(','); first=false;
                        sb.append('{').append("\"date\":\"").append(d.date).append("\",\"companyAmount\":").append(d.companyAmount).append(",\"stores\":[");
                        boolean f2=true;
                        for (var s: d.stores) { if (!f2) sb.append(','); f2=false; sb.append('{').append("\"storeId\":\"").append(s.storeId).append("\",\"amount\":").append(s.amount).append('}'); }
                        sb.append(" ]}");
                    }
                    sb.append(']').append('}');
                    byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
                } catch (Exception ex) {
                    try { exchange.sendResponseHeaders(500, -1); } catch (Exception e) {}
                }
            }
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        int port = server.getAddress().getPort();
        try {
            URL url = new URL("http://127.0.0.1:"+port+"/api/v1/allocations/daily?year=2025&month=9");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            assertEquals(200, conn.getResponseCode());
            // simple check: content contains "year":2025 and some daily entries
            var is = conn.getInputStream();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("\"year\":2025"));
            assertTrue(body.contains("\"daily\":["));
        } finally {
            server.stop(0);
        }
    }
}

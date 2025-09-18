package com.kenfukuda.dashboard.domain.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ApportionServiceTest {

    private final ApportionService svc = new ApportionService();

    @Test
    public void basicAllocation() {
        Map<String, Double> w = new LinkedHashMap<>();
        w.put("A", 3.0);
        w.put("B", 2.0);
        w.put("C", 1.0);

        Map<String, Integer> out = svc.hamiltonApportion(w, 6);
        assertEquals(3, out.get("A"));
        assertEquals(2, out.get("B"));
        assertEquals(1, out.get("C"));
    }

    @Test
    public void fractionalTieBreak() {
        Map<String, Double> w = new LinkedHashMap<>();
        // quotas: A=1.4, B=1.4, C=1.2  (sum=6, seats=4)
        w.put("A", 1.4);
        w.put("B", 1.4);
        w.put("C", 1.2);
        Map<String, Integer> out = svc.hamiltonApportion(w, 4);
        int sum = out.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(4, sum);
        // deterministic tie-break: frac A==B, weight equal => key order A before B
        assertTrue(out.get("A") >= out.get("B"));
    }

    @Test
    public void zeroWeights() {
        Map<String, Double> w = new LinkedHashMap<>();
        w.put("A", 0.0);
        w.put("B", 0.0);
        Map<String, Integer> out = svc.hamiltonApportion(w, 5);
        assertEquals(0, out.get("A"));
        assertEquals(0, out.get("B"));
    }

    @Test
    public void zeroSeats() {
        Map<String, Double> w = new LinkedHashMap<>();
        w.put("A", 10.0);
        w.put("B", 10.0);
        Map<String, Integer> out = svc.hamiltonApportion(w, 0);
        assertEquals(0, out.get("A"));
        assertEquals(0, out.get("B"));
    }

    @Test
    public void negativeWeightThrows() {
        Map<String, Double> w = new LinkedHashMap<>();
        w.put("A", -1.0);
        Exception ex = assertThrows(IllegalArgumentException.class, () -> svc.hamiltonApportion(w, 5));
        assertTrue(ex.getMessage().contains("non-negative") || ex.getMessage().contains("weight"));
    }
}

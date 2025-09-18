package com.kenfukuda.dashboard.domain.service;

import java.util.*;

/**
 * Simple implementation of Hamilton apportionment method.
 */
public class ApportionService {

    public Map<String, Integer> hamiltonApportion(Map<String, Double> weights, int totalSeats) {
        if (weights == null) throw new IllegalArgumentException("weights cannot be null");
        if (totalSeats < 0) throw new IllegalArgumentException("totalSeats must be non-negative");

        Map<String, Integer> result = new LinkedHashMap<>();
        if (totalSeats == 0) {
            for (String k : weights.keySet()) result.put(k, 0);
            return result;
        }

        double sum = 0.0;
        for (Double v : weights.values()) {
            if (v == null) throw new IllegalArgumentException("weight value cannot be null");
            if (v < 0) throw new IllegalArgumentException("weight values must be non-negative");
            sum += v;
        }

        if (sum == 0.0) {
            for (String k : weights.keySet()) result.put(k, 0);
            return result;
        }

        // Compute quotas and floors
        class Item { String key; double weight; double quota; int floor; double frac; }

        List<Item> items = new ArrayList<>();
        int assigned = 0;
        for (Map.Entry<String, Double> e : weights.entrySet()) {
            Item it = new Item();
            it.key = e.getKey();
            it.weight = e.getValue();
            it.quota = (it.weight * totalSeats) / sum;
            it.floor = (int) Math.floor(it.quota + 1e-12);
            it.frac = it.quota - it.floor;
            assigned += it.floor;
            items.add(it);
        }

        int remaining = totalSeats - assigned;

        // Sort by fractional part desc, then by weight desc, then by key asc for deterministic tie-break
        items.sort((a, b) -> {
            int c = Double.compare(b.frac, a.frac);
            if (c != 0) return c;
            c = Double.compare(b.weight, a.weight);
            if (c != 0) return c;
            return a.key.compareTo(b.key);
        });

        // assign remaining seats
        for (Item it : items) {
            int seats = it.floor + (remaining > 0 ? 1 : 0);
            if (remaining > 0) remaining--;
            result.put(it.key, seats);
        }

        // If remaining still > 0 (shouldn't happen), distribute round-robin
        if (remaining > 0) {
            for (String k : result.keySet()) {
                if (remaining == 0) break;
                result.put(k, result.get(k) + 1);
                remaining--;
            }
        }

        return result;
    }
}

package com.kenfukuda.dashboard.allocation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

public class AllocationServiceTest {

    @Test
    public void testDistribute30Days() {
        AllocationService svc = new AllocationService();
        List<AllocationService.DailyEntry> res = svc.distributeMonthlyToDaily(100.0, 2025, 9, "last-day");
        double sum = res.stream().mapToDouble(AllocationService.DailyEntry::getAmount).sum();
        assertEquals(100.0, Math.round(sum * 100) / 100.0);
    }

    @Test
    public void test29And31Days() {
        AllocationService svc = new AllocationService();
        List<AllocationService.DailyEntry> r29 = svc.distributeMonthlyToDaily(100.0, 2024, 2, "last-day");
        List<AllocationService.DailyEntry> r31 = svc.distributeMonthlyToDaily(100.0, 2025, 7, "last-day");
        double s29 = r29.stream().mapToDouble(AllocationService.DailyEntry::getAmount).sum();
        double s31 = r31.stream().mapToDouble(AllocationService.DailyEntry::getAmount).sum();
        assertEquals(100.0, Math.round(s29 * 100) / 100.0);
        assertEquals(100.0, Math.round(s31 * 100) / 100.0);
    }
}

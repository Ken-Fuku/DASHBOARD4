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

    @Test
    public void testPriorityAllocationAndAbnormal() {
        AllocationService svc = new AllocationService();
        List<AllocationService.StoreMonthly> stores = List.of(new AllocationService.StoreMonthly("A", 600.0), new AllocationService.StoreMonthly("B", 300.0));
        List<AllocationService.DailyComposite> comp = svc.allocateCompanyThenStores(300.0, stores, 2025, 9, "last-day");
        // company 300 + stores 900 -> total 1200 -> per day (Sep 2025 = 30 days) approx 40.00
        double dailySum0 = Math.round((comp.get(0).companyAmount + comp.get(0).stores.stream().mapToDouble(s->s.amount).sum())*100)/100.0;
        assertEquals(40.0, dailySum0);

        // abnormal: negative amount -> expect IllegalArgumentException
        try {
            svc.distributeMonthlyToDaily(-1.0, 2025, 9, "last-day");
            throw new AssertionError("expected exception for negative amount");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }
}

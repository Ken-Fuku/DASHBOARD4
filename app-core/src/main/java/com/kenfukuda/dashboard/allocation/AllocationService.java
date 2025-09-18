package com.kenfukuda.dashboard.allocation;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AllocationService {

    public static class DailyEntry {
        public final String date;
        public final long cents;

        public DailyEntry(String date, long cents) {
            this.date = date;
            this.cents = cents;
        }

        public double getAmount() { return cents / 100.0; }
    }

    private static int daysInMonth(int year, int month) {
        return LocalDate.of(year, month, 1).lengthOfMonth();
    }

    private static long toCents(double amount) {
        return Math.round(amount * 100);
    }

    public List<DailyEntry> distributeMonthlyToDaily(double monthAmount, int year, int month, String roundingStrategy) {
        int days = daysInMonth(year, month);
        long total = toCents(monthAmount);
        long base = total / days;
        long remainder = total - base * days;

        List<Long> cents = new ArrayList<>();
        for (int i = 0; i < days; i++) cents.add(base);

        if (remainder > 0) {
            if ("last-day".equals(roundingStrategy) || roundingStrategy == null) {
                cents.set(days - 1, cents.get(days - 1) + remainder);
            } else if ("spread".equals(roundingStrategy)) {
                for (int i = 0; i < remainder; i++) cents.set(i, cents.get(i) + 1);
            }
        }

        List<DailyEntry> out = new ArrayList<>();
        DateTimeFormatter f = DateTimeFormatter.ISO_LOCAL_DATE;
        for (int i = 0; i < days; i++) {
            LocalDate d = LocalDate.of(year, month, i + 1);
            out.add(new DailyEntry(d.format(f), cents.get(i)));
        }
        return out;
    }

    public static class StoreMonthly { public final String storeId; public final double monthlyAmount; public StoreMonthly(String storeId, double monthlyAmount){this.storeId=storeId;this.monthlyAmount=monthlyAmount;} }

    public static class DailyComposite {
        public final String date;
        public final double companyAmount;
        public final List<StoreAmount> stores;
        public DailyComposite(String date, double companyAmount, List<StoreAmount> stores){this.date=date;this.companyAmount=companyAmount;this.stores=stores;}
    }

    public static class StoreAmount { public final String storeId; public final double amount; public StoreAmount(String storeId, double amount){this.storeId=storeId;this.amount=amount;} }

    public List<DailyComposite> allocateCompanyThenStores(double companyMonthly, List<StoreMonthly> stores, int year, int month, String roundingStrategy) {
        List<DailyEntry> companyDaily = distributeMonthlyToDaily(companyMonthly, year, month, roundingStrategy);
        List<List<DailyEntry>> storesDaily = new ArrayList<>();
        for (StoreMonthly s : stores) storesDaily.add(distributeMonthlyToDaily(s.monthlyAmount, year, month, roundingStrategy));

        List<DailyComposite> out = new ArrayList<>();
        int days = companyDaily.size();
        for (int i = 0; i < days; i++) {
            String date = companyDaily.get(i).date;
            double compAmt = companyDaily.get(i).getAmount();
            List<StoreAmount> sList = new ArrayList<>();
            for (int j = 0; j < stores.size(); j++) {
                StoreMonthly s = stores.get(j);
                double a = storesDaily.get(j).get(i).getAmount();
                sList.add(new StoreAmount(s.storeId, a));
            }
            out.add(new DailyComposite(date, compAmt, sList));
        }
        return out;
    }
}

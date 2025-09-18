package com.kenfukuda.dashboard.allocation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
public class AllocationController {

    private final AllocationService svc = new AllocationService();

    @GetMapping("/api/v1/allocations/daily")
    public Object getDaily(@RequestParam int year,
                           @RequestParam int month,
                           @RequestParam(required = false) Double companyAmount,
                           @RequestParam(required = false) String stores // format: id:amount|id2:amount2
    ) {
        if (year <= 0 || month <= 0 || month > 12) return new ErrorResponse("invalid params");
        double comp = companyAmount == null ? 0.0 : companyAmount;
        List<AllocationService.StoreMonthly> storeList = List.of();
        if (stores != null && !stores.isBlank()) {
            var parts = stores.split("\\|");
            var temp = new java.util.ArrayList<AllocationService.StoreMonthly>();
            for (String p : parts) {
                var kv = p.split(":");
                if (kv.length != 2) return new ErrorResponse("invalid store format");
                temp.add(new AllocationService.StoreMonthly(kv[0], Double.parseDouble(kv[1])));
            }
            storeList = temp;
        }

        List<AllocationService.DailyComposite> compDaily = svc.allocateCompanyThenStores(comp, storeList, year, month, "last-day");
        var dailyOut = compDaily.stream().map(d -> new CompositeOut(d.date, d.companyAmount, d.stores)).collect(Collectors.toList());
        return new CompositeResponse(year, month, dailyOut);
    }

    static class DailyOut { public final String date; public final double amount; public DailyOut(String date, double amount){this.date=date;this.amount=amount;} }
    static class AllocationResponse { public final int year; public final int month; public final List<DailyOut> daily; public final double totalMonthly; public AllocationResponse(int year,int month,List<DailyOut> daily,double totalMonthly){this.year=year;this.month=month;this.daily=daily;this.totalMonthly=totalMonthly;} }
    static class ErrorResponse { public final String error; public ErrorResponse(String error){this.error=error;} }
    static class CompositeOut { public final String date; public final double companyAmount; public final List<AllocationService.StoreAmount> stores; public CompositeOut(String date,double companyAmount,List<AllocationService.StoreAmount> stores){this.date=date;this.companyAmount=companyAmount;this.stores=stores;} }
    static class CompositeResponse { public final int year; public final int month; public final List<CompositeOut> daily; public CompositeResponse(int year,int month,List<CompositeOut> daily){this.year=year;this.month=month;this.daily=daily;} }
}

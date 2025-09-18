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
    public Object getDaily(@RequestParam int year, @RequestParam int month, @RequestParam(required = false) Double amount) {
        if (year <= 0 || month <= 0 || month > 12) return new ErrorResponse("invalid params");
        double amt = amount == null ? 0.0 : amount;
        List<AllocationService.DailyEntry> daily = svc.distributeMonthlyToDaily(amt, year, month, "last-day");
        var dailyOut = daily.stream().map(d -> new DailyOut(d.date, d.getAmount())).collect(Collectors.toList());
        return new AllocationResponse(year, month, dailyOut, amt);
    }

    static class DailyOut { public final String date; public final double amount; public DailyOut(String date, double amount){this.date=date;this.amount=amount;} }
    static class AllocationResponse { public final int year; public final int month; public final List<DailyOut> daily; public final double totalMonthly; public AllocationResponse(int year,int month,List<DailyOut> daily,double totalMonthly){this.year=year;this.month=month;this.daily=daily;this.totalMonthly=totalMonthly;} }
    static class ErrorResponse { public final String error; public ErrorResponse(String error){this.error=error;} }
}

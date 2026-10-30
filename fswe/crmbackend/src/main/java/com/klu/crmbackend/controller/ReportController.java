package com.klu.crmbackend.controller;
import com.klu.crmbackend.service.ReportService;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
@RestController @RequestMapping("/api/reports")
public class ReportController {
    private final ReportService reports;public ReportController(ReportService reports){this.reports=reports;}
    @GetMapping public Object report(@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to){return reports.summary(from==null?LocalDate.now().withDayOfMonth(1):from,to==null?LocalDate.now():to);}
}

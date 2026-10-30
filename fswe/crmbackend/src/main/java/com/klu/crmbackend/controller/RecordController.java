package com.klu.crmbackend.controller;
import com.klu.crmbackend.service.RecordQuery;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/records")
public class RecordController {
    private final RecordQuery query;
    public RecordController(RecordQuery query) { this.query=query; }
    @GetMapping("/{kind}") public RecordQuery.PageResult page(@PathVariable String kind,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
        @RequestParam(defaultValue="") String search,@RequestParam(defaultValue="ALL") String status,
        @RequestParam(defaultValue="") String sort,@RequestParam(defaultValue="true") boolean desc,@RequestParam(required=false) Long customerId,@RequestParam(required=false) Long dealId) {
        return query.page(kind,page,size,search,status,sort,desc,customerId,dealId);
    }
}

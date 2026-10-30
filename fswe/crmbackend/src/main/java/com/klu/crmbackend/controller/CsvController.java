package com.klu.crmbackend.controller;
import com.klu.crmbackend.service.CsvService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@RestController @RequestMapping("/api/data/{kind}")
public class CsvController {
    private final CsvService csv;public CsvController(CsvService csv){this.csv=csv;}
    @PostMapping(value="/import",consumes="text/plain") public Object preview(@PathVariable String kind,@RequestParam(defaultValue="false") boolean commit,@RequestBody String body){return csv.importCsv(kind,body,commit);}
    @GetMapping("/export") public ResponseEntity<String> export(@PathVariable String kind){return ResponseEntity.ok().header("Content-Disposition","attachment; filename=\""+kind+".csv\"").contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(csv.export(kind));}
}

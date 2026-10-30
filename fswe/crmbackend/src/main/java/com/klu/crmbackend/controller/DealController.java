package com.klu.crmbackend.controller;

import com.klu.crmbackend.model.Deal;
import com.klu.crmbackend.service.DealService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deals")
public class DealController {

    private final DealService dealService;

    public DealController(DealService dealService) {
        this.dealService = dealService;
    }

    // GET all deals
    @GetMapping
    public List<Deal> getAllDeals() {
        return dealService.getAllDeals();
    }

    // GET deal by ID
    @GetMapping("/{id}")
    public ResponseEntity<Deal> getDealById(@PathVariable Long id) {

        return dealService.getDealById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // CREATE deal
    @PostMapping
    public Deal createDeal(@RequestBody Deal deal) {
        return dealService.createDeal(deal);
    }

    // UPDATE deal
    @PutMapping("/{id}")
    public ResponseEntity<Deal> updateDeal(
            @PathVariable Long id,
            @RequestBody Deal dealDetails) {

            return ResponseEntity.ok(
                    dealService.updateDeal(id, dealDetails)
            );
    }

    public record StageRequest(String stage,String lostReason) {}
    @PatchMapping("/{id}/stage") public Deal stage(@PathVariable Long id,@RequestBody StageRequest body) {return dealService.changeStage(id,body.stage(),body.lostReason());}
    // DELETE deal
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDeal(@PathVariable Long id) {

        dealService.deleteDeal(id);

        return ResponseEntity.noContent().build();
    }
}
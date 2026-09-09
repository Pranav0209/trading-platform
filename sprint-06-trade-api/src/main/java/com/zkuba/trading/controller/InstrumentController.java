package com.zkuba.trading.controller;

import com.zkuba.trading.dto.response.InstrumentResponse;
import com.zkuba.trading.service.InstrumentService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/instruments")
public class InstrumentController {
    private final InstrumentService instrumentService;

    public InstrumentController(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    @GetMapping
    public List<InstrumentResponse> all() {
        return instrumentService
                .all()
                .stream()
                .map(
                        i ->
                                new InstrumentResponse(
                                        i.getSymbol(),
                                        i.getDisplayName(),
                                        i.getAssetClass(),
                                        i.getCurrency(),
                                        i.isTradable()))
                .toList();
    }

    @GetMapping("/{symbol}")
    public InstrumentResponse one(@PathVariable String symbol) {
        var i = instrumentService.get(symbol);
        return new InstrumentResponse(
                i.getSymbol(), i.getDisplayName(), i.getAssetClass(), i.getCurrency(), i.isTradable());
    }
}


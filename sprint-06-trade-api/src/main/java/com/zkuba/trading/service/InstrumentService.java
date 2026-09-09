package com.zkuba.trading.service;

import com.zkuba.trading.domain.entity.Instrument;
import com.zkuba.trading.domain.exception.InstrumentNotFoundException;
import com.zkuba.trading.repository.InstrumentRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class InstrumentService {
    private final InstrumentRepository instruments;

    public InstrumentService(InstrumentRepository instruments) {
        this.instruments = instruments;
    }

    public List<Instrument> all() {
        return instruments.findAll();
    }

    public Instrument get(String symbol) {
        return instruments
                .findById(symbol.toUpperCase())
                .orElseThrow(() -> new InstrumentNotFoundException(symbol));
    }
}

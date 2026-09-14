package com.zkuba.trading.service;

import com.zkuba.trading.domain.entity.Instrument;
import java.util.List;

public interface InstrumentService {

    List<Instrument> all();

    Instrument get(String symbol);
}

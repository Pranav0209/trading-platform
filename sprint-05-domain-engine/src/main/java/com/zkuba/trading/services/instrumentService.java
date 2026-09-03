package com.zkuba.trading.services;

import com.zkuba.trading.domain.model.Instrument;
import com.zkuba.trading.domain.exception.InstrumentNotFoundException;

public class instrumentService{

    public boolean instrumentExists(String symbol){
        // check w repo
            if (symbol == null){
                throw new InstrumentNotFoundException(symbol);
            }
            return true;
        }
    }
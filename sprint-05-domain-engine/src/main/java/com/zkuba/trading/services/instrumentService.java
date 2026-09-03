package com.zkuba.trading.services;

import com.zkuba.trading.domain.model.Instrument;
import org.springframework.stereotype.Service;


@Service
public class instrumentService{

    public boolean instrumentExists(String symbol){
        // check w repo
            if (symbol == null){
                throw new InstrumentNotFoundException();
            }
            return true;
        }
    }
}
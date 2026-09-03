package com.zkuba.trading.services;

import com.zkuba.trading.domain.model.Order;
import java.util.UUID;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class orderService{
    List<String> dummyIdempotencyKeys = List.of(
            '43905jsdfk', 'sdajf', '8q4735sad'
    )
    public boolean checkIdempotency(String idempotencyKey){
        if (dummyIdempotencyKeys.contains(idempotencyKey)){
            System.out.println("ORD-409 Order already found");
            return false;
        }
        return true;
    }
}
package com.zkuba.trading.services;

import com.zkuba.trading.domain.model.Account;
import org.springframework.stereotype.Service;


@Service
public class accountService{

    public boolean accountExists(Long accountId){
        if (accountId == null){
            throw new AccountNotFoundException();
        }
        return true;
    }
}
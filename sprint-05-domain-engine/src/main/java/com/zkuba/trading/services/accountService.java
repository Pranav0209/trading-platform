package com.zkuba.trading.services;

import com.zkuba.trading.domain.model.Account;
import org.springframework.stereotype.Service;


@Service
public class accountService{

    public boolean accountExists(Long accountId){
        // ideally we check the repository to see if the accountId is present or not
        if (accountId == null){
            throw new AccountNotFoundException();
        }
        return true;
    }
}
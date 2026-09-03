package com.zkuba.trading.services;
import com.zkuba.trading.domain.exception.AccountNotFoundException;

public class accountService{

    public boolean accountExists(Long accountId){
        // ideally we check the repository to see if the accountId is present or not
        if (accountId == null){
            throw new AccountNotFoundException(accountId);
        }
        return true;
    }
}
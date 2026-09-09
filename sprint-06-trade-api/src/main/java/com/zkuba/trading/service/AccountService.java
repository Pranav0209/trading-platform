package com.zkuba.trading.service;

import com.zkuba.trading.domain.entity.Account;
import com.zkuba.trading.domain.entity.Position;
import com.zkuba.trading.domain.enums.AccountStatus;
import com.zkuba.trading.domain.exception.AccountAlreadyExistsException;
import com.zkuba.trading.domain.exception.AccountNotActiveException;
import com.zkuba.trading.domain.exception.AccountNotFoundException;
import com.zkuba.trading.dto.request.CreateAccountRequest;
import com.zkuba.trading.dto.response.AccountResponse;
import com.zkuba.trading.repository.AccountRepository;
import com.zkuba.trading.repository.PositionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final PositionRepository positionRepository;
    private final UserService userService;
    private final BigDecimal defaultBalance;

    public AccountService(
            AccountRepository accounts,
            PositionRepository positionRepository,
            UserService userService,
            @Value("${trading.default-initial-balance}") BigDecimal defaultBalance) {
        this.accounts = accounts;
        this.positionRepository = positionRepository;
        this.userService = userService;
        this.defaultBalance = defaultBalance;
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        userService.get(request.userId());
        if (accounts.findByUserId(request.userId()).isPresent()) {
            throw new AccountAlreadyExistsException(request.userId());
        }
        Account account = new Account();
        account.setAccountNumber("ACC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        account.setUserId(request.userId());
        account.setCashBalance(defaultBalance);
        account.setCurrency(request.currency() == null ? "USD" : request.currency());
        account.setStatus(AccountStatus.ACTIVE);
        account.setVersion(0);
        accounts.save(account);
        return response(account);
    }

    public Account getByUserId(Long userId) {
        Account account = accounts.findByUserId(userId)
                .orElseThrow(() -> new AccountNotFoundException(userId));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(account.getId());
        }
        return account;
    }

    public AccountResponse responseByUser(Long userId) {
        return response(getByUserId(userId));
    }

    public List<Position> getPositions(Long userId) {
        return positionRepository.findByAccountId(getByUserId(userId).getId());
    }

    private AccountResponse response(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getUserId(),
                account.getCashBalance(),
                account.getCurrency(),
                account.getStatus().name());
    }
}



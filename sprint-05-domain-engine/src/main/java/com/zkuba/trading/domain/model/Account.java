package com.zkuba.trading.domain.model;

import com.zkuba.trading.domain.enums.AccountStatus;
import com.zkuba.trading.domain.exception.AccountNotActiveException;
import com.zkuba.trading.domain.exception.InsufficientFundsException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.Objects;
public class Account {

    @NotNull
    @Positive
    private final Long accountId;
    @NotBlank
    private final String accountNumber;
    @NotBlank
    private final String holder;
    @NotNull
    @PositiveOrZero
    private BigDecimal cashBalance;
    @NotNull
    private AccountStatus status;
    @NotNull
    @PositiveOrZero
    private final Long version;

    public Account(
            Long accountId,
            String accountNumber,
            String holder,
            BigDecimal cashBalance,
            AccountStatus status,
            Long version) {

        this.accountId = Objects.requireNonNull(accountId);
        this.accountNumber = Objects.requireNonNull(accountNumber);
        this.holder = Objects.requireNonNull(holder);
        this.cashBalance = Objects.requireNonNull(cashBalance);
        this.status = Objects.requireNonNull(status);
        this.version = Objects.requireNonNull(version);

        if (this.cashBalance.signum() < 0) {
            throw new IllegalArgumentException("Cash balance cannot be negative");
        }
    }

    public boolean canAfford(BigDecimal amount) {
        requirePositive(amount);
        return cashBalance.compareTo(amount) >= 0;
    }

    // Rule 6: a buy's cost is refused here, before anything is subtracted.
    public void debit(BigDecimal amount) {
        requirePositive(amount);

        if (!this.accountIsActive()) {
            throw new AccountNotActiveException(accountId);
        }

        if (!canAfford(amount)) {
            throw new InsufficientFundsException(accountId);
        }

        cashBalance = cashBalance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        requirePositive(amount);

        if (!this.accountIsActive()) {
            throw new AccountNotActiveException(accountId);
        }

        cashBalance = cashBalance.add(amount);
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getHolder() {
        return holder;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }

    private static void requirePositive(BigDecimal value) {
        Objects.requireNonNull(value);

        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    // second business rule
    public boolean accountIsActive(){
        if (this.status == AccountStatus.ACTIVE){
            return true;
        }
        return false;
    }

}
package com.zkuba.trading.domain;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AccountTest {

    //immutable
    private static Account activeAccount;
    
    //mutable
    private Account mutableAccount;

    @BeforeAll
    static void setupShared() {
        activeAccount = new Account(
                181L,
                "TRD000181",
                "Krishnendu",
                new BigDecimal("1000.00"),
                AccountStatus.ACTIVE,
                3L
        );
    }

    @BeforeEach
    void setupMutable() {
        mutableAccount = new Account(
                181L,
                "TRD000181",
                "Krishnendu",
                new BigDecimal("1000.00"),
                AccountStatus.ACTIVE,
                3L
        );
    }

    // Simple getter checks - use shared account
    @Test
    void version() {
        assertEquals(3L, activeAccount.getVersion());
    }

    @Test
    void balance() {
        assertEquals(new BigDecimal("1000.00"), activeAccount.getCashBalance());
    }

    @Test
    void status() {
        assertEquals(AccountStatus.ACTIVE, activeAccount.getStatus());
    }

    // Edge case checks - create new accounts
    @Test
    void negativeBalanceRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Account(
                        999L,
                        "TRD000999",
                        "Holder with neg balance",
                        new BigDecimal("-100.00"),
                        AccountStatus.ACTIVE,
                        1L
                )
        );
    }

    // Business logic checks - use mutable account from @BeforeEach
    @Test
    void debitCutsCash() {
        mutableAccount.debit(new BigDecimal("250.00"));
        assertEquals(new BigDecimal("750.00"), mutableAccount.getCashBalance());
    }

    @Test
    void creditAddsCash() {
        mutableAccount.credit(new BigDecimal("250.00"));
        assertEquals(new BigDecimal("1250.00"), mutableAccount.getCashBalance());
    }

    @Test
    void debitFailsWhenInsufficientFunds() {
        assertThrows(
                InsufficientFundsException.class,
                () -> mutableAccount.debit(new BigDecimal("1000.12"))
        );
        assertEquals(new BigDecimal("1000.00"), mutableAccount.getCashBalance());
    }

    @Test
    void debitFailsWithNegativeAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mutableAccount.debit(new BigDecimal("-12.00"))
        );
        assertEquals(new BigDecimal("1000.00"), mutableAccount.getCashBalance());
    }

    @Test
    void suspendedAccountFailsDebit() {
        Account suspendedAccount = new Account(
                200L,
                "TRD000200",
                "Krishnendu",
                new BigDecimal("500.00"),
                AccountStatus.SUSPENDED,
                8L
        );

        assertThrows(
                AccountNotActiveException.class,
                () -> suspendedAccount.debit(new BigDecimal("10.00"))
        );
    }

    @Test
    void suspendedAccountFailsCredit() {
        Account suspendedAccount = new Account(
                203L,
                "TRD000203",
                "Holder with a suspended account",
                new BigDecimal("500.00"),
                AccountStatus.SUSPENDED,
                9L
        );

        assertThrows(
                AccountNotActiveException.class,
                () -> suspendedAccount.credit(new BigDecimal("100.00"))
        );
    }

    @Test
    void balanceStaysStable() {
        for (int i = 0; i < 100; i++) {
            mutableAccount.debit(new BigDecimal("1.25"));
            mutableAccount.credit(new BigDecimal("1.25"));
        }
        assertEquals(new BigDecimal("1000.00"), mutableAccount.getCashBalance());
    }
}



package com.zkuba.trading.service;

import com.zkuba.trading.domain.entity.Account;
import com.zkuba.trading.domain.entity.Position;
import com.zkuba.trading.domain.entity.User;
import com.zkuba.trading.domain.enums.AccountStatus;
import com.zkuba.trading.exception.AccountAlreadyExistsException;
import com.zkuba.trading.exception.AccountNotActiveException;
import com.zkuba.trading.exception.AccountNotFoundException;
import com.zkuba.trading.dto.request.CreateAccountRequest;
import com.zkuba.trading.dto.response.AccountResponse;
import com.zkuba.trading.repository.AccountRepository;
import com.zkuba.trading.repository.PositionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accounts;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private AccountServiceImpl accountService;

    private BigDecimal defaultBalance;
    private Account activeAccount;

    @BeforeEach
    void setUp() {
        defaultBalance = new BigDecimal("10000.00");
        ReflectionTestUtils.setField(accountService, "defaultBalance", defaultBalance);

        activeAccount = new Account();
        activeAccount.setId(100L);
        activeAccount.setUserId(1L);
        activeAccount.setAccountNumber("ACC-12345678");
        activeAccount.setCashBalance(defaultBalance);
        activeAccount.setCurrency("USD");
        activeAccount.setStatus(AccountStatus.ACTIVE);
        activeAccount.setVersion(0);
    }

    @Test
    @DisplayName("create should successfully create a new account with default currency")
    void create_Success_DefaultCurrency() {
        CreateAccountRequest request = new CreateAccountRequest(1L, null);
        when(userService.get(1L)).thenReturn(new User());
        when(accounts.findByUserId(1L)).thenReturn(Optional.empty());

        AccountResponse response = accountService.create(request);

        assertThat(response).isNotNull();
        assertThat(response.cashBalance()).isEqualTo(defaultBalance);
        assertThat(response.status()).isEqualTo("ACTIVE");

        verify(accounts).save(any(Account.class));
    }

    @Test
    @DisplayName("create should successfully create a new account with custom currency")
    void create_Success_CustomCurrency() {
        CreateAccountRequest request = new CreateAccountRequest(1L, "EUR");
        when(userService.get(1L)).thenReturn(new User());
        when(accounts.findByUserId(1L)).thenReturn(Optional.empty());

        AccountResponse response = accountService.create(request);

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("create should throw AccountAlreadyExistsException if user already has an account")
    void create_AlreadyExists_ThrowsException() {
        CreateAccountRequest request = new CreateAccountRequest(1L, "USD");
        when(userService.get(1L)).thenReturn(new User());
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> accountService.create(request))
                .isInstanceOf(AccountAlreadyExistsException.class);
    }

    @Test
    @DisplayName("getByUserId should return active account")
    void getByUserId_Success() {
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(activeAccount));

        Account account = accountService.getByUserId(1L);

        assertThat(account).isNotNull();
        assertThat(account.getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("getByUserId should throw AccountNotFoundException when account does not exist")
    void getByUserId_NotFound_ThrowsException() {
        when(accounts.findByUserId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getByUserId(99L))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("getByUserId should throw AccountNotActiveException when account is suspended")
    void getByUserId_Inactive_ThrowsException() {
        activeAccount.setStatus(AccountStatus.SUSPENDED);
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> accountService.getByUserId(1L))
                .isInstanceOf(AccountNotActiveException.class);
    }

    @Test
    @DisplayName("responseByUser should return AccountResponse for user")
    void responseByUser_Success() {
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(activeAccount));

        AccountResponse response = accountService.responseByUser(1L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.accountId()).isEqualTo("ACC-12345678");
    }

    @Test
    @DisplayName("getPositions should return positions list for user's account")
    void getPositions_Success() {
        Position p1 = new Position();
        p1.setAccountId(100L);
        p1.setSymbol("AAPL");
        p1.setQuantity(10);
        p1.setAverageCost(new BigDecimal("150.00"));

        when(accounts.findByUserId(1L)).thenReturn(Optional.of(activeAccount));
        when(positionRepository.findByAccountId(100L)).thenReturn(List.of(p1));

        List<Position> positions = accountService.getPositions(1L);

        assertThat(positions).hasSize(1);
        assertThat(positions.get(0).getSymbol()).isEqualTo("AAPL");
    }
}

package com.zkuba.trading.service;

import com.zkuba.trading.domain.entity.Account;
import com.zkuba.trading.domain.entity.Order;
import com.zkuba.trading.domain.entity.Position;
import com.zkuba.trading.domain.enums.AccountStatus;
import com.zkuba.trading.domain.enums.OrderStatus;
import com.zkuba.trading.exception.AccountAlreadyExistsException;
import com.zkuba.trading.exception.AccountNotActiveException;
import com.zkuba.trading.exception.AccountNotFoundException;
import com.zkuba.trading.exception.ForbiddenException;
import com.zkuba.trading.dto.request.CreateAccountRequest;
import com.zkuba.trading.dto.response.AccountResponse;
import com.zkuba.trading.dto.response.BalanceResponse;
import com.zkuba.trading.dto.response.OrderHistoryEntry;
import com.zkuba.trading.dto.response.PositionResponse;
import com.zkuba.trading.mapper.OrderMapper;
import com.zkuba.trading.repository.AccountRepository;
import com.zkuba.trading.repository.PositionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountServiceImpl implements AccountService {

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private OrderMapper orderMapper;

    @Value("${trading.default-initial-balance}")
    private BigDecimal defaultBalance;

    @Override
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

    @Override
    public Account getByUserId(Long userId) {
        Account account = accounts.findByUserId(userId)
                .orElseThrow(() -> new AccountNotFoundException(userId));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(account.getId());
        }
        return account;
    }

    @Override
    public Account getAccountAndValidateAccess(Long accountId, Long userId) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        if (!account.getUserId().equals(userId)) {
            throw new ForbiddenException("ACC-403", "Account does not belong to token user");
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(accountId);
        }
        return account;
    }

    @Override
    public AccountResponse getAccountResponseById(Long accountId, Long userId) {
        Account account = getAccountAndValidateAccess(accountId, userId);
        return response(account);
    }

    @Override
    public AccountResponse responseByUser(Long userId) {
        return response(getByUserId(userId));
    }

    @Override
    public BalanceResponse getBalance(Long accountId, Long userId) {
        Account account = getAccountAndValidateAccess(accountId, userId);
        return new BalanceResponse(
                account.getId(),
                account.getCashBalance(),
                account.getCurrency() == null ? "USD" : account.getCurrency(),
                Instant.now().toString());
    }

    @Override
    public List<PositionResponse> getPositionsResponse(Long accountId, Long userId) {
        Account account = getAccountAndValidateAccess(accountId, userId);
        List<Position> positions = positionRepository.findByAccountId(account.getId());
        return positions.stream()
                .filter(p -> p.getQuantity() > 0)
                .map(p -> new PositionResponse(account.getId(), p.getSymbol(), p.getQuantity(), p.getAverageCost()))
                .toList();
    }

    @Override
    public List<Position> getPositions(Long userId) {
        return positionRepository.findByAccountId(getByUserId(userId).getId());
    }

    @Override
    public List<OrderHistoryEntry> getOrderHistory(
            Long accountId,
            OrderStatus status,
            OffsetDateTime from,
            OffsetDateTime to,
            Long userId) {
        Account account = getAccountAndValidateAccess(accountId, userId);
        List<Order> orders = orderMapper.findByAccountIdFiltered(
                account.getId(),
                status != null ? status.name() : null,
                from,
                to);
        return orders.stream()
                .map(order -> new OrderHistoryEntry(
                        "ORD-" + order.getId(),
                        order.getAccountId(),
                        order.getSymbol(),
                        order.getSide().name(),
                        order.getQuantity(),
                        order.getLimitPrice(),
                        order.getExecutedPrice(),
                        order.getStatus().name(),
                        order.getIdempotencyKey(),
                        order.getCreatedAt() != null ? order.getCreatedAt().toString() : null))
                .toList();
    }

    private AccountResponse response(Account account) {
        String holderName;
        try {
            holderName = userService.get(account.getUserId()).getUsername();
        } catch (Exception e) {
            holderName = "User " + account.getUserId();
        }
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                holderName,
                account.getCashBalance(),
                account.getStatus().name(),
                account.getVersion(),
                Instant.now().toString());
    }
}

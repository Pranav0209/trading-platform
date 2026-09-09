package com.zkuba.trading.service;

import com.zkuba.trading.domain.entity.Account;
import com.zkuba.trading.domain.entity.Position;
import com.zkuba.trading.domain.enums.OrderStatus;
import com.zkuba.trading.dto.request.CreateAccountRequest;
import com.zkuba.trading.dto.response.AccountResponse;
import com.zkuba.trading.dto.response.BalanceResponse;
import com.zkuba.trading.dto.response.OrderHistoryEntry;
import com.zkuba.trading.dto.response.PositionResponse;
import java.time.OffsetDateTime;
import java.util.List;

public interface AccountService {

    AccountResponse create(CreateAccountRequest request);

    Account getByUserId(Long userId);

    Account getAccountAndValidateAccess(Long accountId, Long userId);

    AccountResponse getAccountResponseById(Long accountId, Long userId);

    AccountResponse responseByUser(Long userId);

    BalanceResponse getBalance(Long accountId, Long userId);

    List<PositionResponse> getPositionsResponse(Long accountId, Long userId);

    List<Position> getPositions(Long userId);

    List<OrderHistoryEntry> getOrderHistory(
            Long accountId,
            OrderStatus status,
            OffsetDateTime from,
            OffsetDateTime to,
            Long userId);
}

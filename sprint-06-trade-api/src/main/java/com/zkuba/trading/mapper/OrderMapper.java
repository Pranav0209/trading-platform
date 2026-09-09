package com.zkuba.trading.mapper;

import com.zkuba.trading.domain.entity.Account;
import com.zkuba.trading.domain.entity.Order;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderMapper {

    Account findAccountByUserId(@Param("userId") Long userId);

    Account findAccountById(@Param("id") Long id);

    int updateAccountBalance(
            @Param("accountId") Long accountId,
            @Param("newBalance") BigDecimal newBalance,
            @Param("expectedVersion") Integer expectedVersion);

    Order findByIdAndAccountId(@Param("orderId") UUID orderId, @Param("accountId") Long accountId);

    Order findById(@Param("id") UUID id);

    List<Order> findByAccountId(@Param("accountId") Long accountId);

    List<Order> findByAccountIdFiltered(
            @Param("accountId") Long accountId,
            @Param("status") String status,
            @Param("from") java.time.OffsetDateTime from,
            @Param("to") java.time.OffsetDateTime to);

    int insert(Order order);

    int cancelOrderConditionally(
            @Param("orderId") UUID orderId,
            @Param("accountId") Long accountId,
            @Param("expectedStatus") String expectedStatus);
}

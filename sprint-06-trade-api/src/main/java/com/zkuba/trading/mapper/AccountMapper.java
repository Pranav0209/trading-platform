package com.zkuba.trading.mapper;

import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountMapper {
    int updateCashBalance(
            @Param("id") Long id,
            @Param("cashBalance") BigDecimal cashBalance,
            @Param("expectedVersion") Integer expectedVersion);
}

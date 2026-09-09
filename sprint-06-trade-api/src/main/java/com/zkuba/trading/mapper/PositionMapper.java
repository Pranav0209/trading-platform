package com.zkuba.trading.mapper;

import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PositionMapper {
    int upsert(
            @Param("accountId") Long accountId,
            @Param("symbol") String symbol,
            @Param("quantity") int quantity,
            @Param("averageCost") BigDecimal averageCost);
}

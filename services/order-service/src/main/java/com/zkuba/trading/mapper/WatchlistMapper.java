package com.zkuba.trading.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WatchlistMapper {
    List<String> findSymbols(Long id);

    int addSymbol(@Param("watchlistId") Long id, @Param("symbol") String symbol);

    int removeSymbol(@Param("watchlistId") Long id, @Param("symbol") String symbol);

    int removeAllSymbols(@Param("watchlistId") Long id);
}

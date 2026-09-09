package com.zkuba.trading.repository;

import com.zkuba.trading.domain.entity.Watchlist;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {
    List<Watchlist> findByUserId(Long userId);
}

package com.zkuba.trading.service;

import com.zkuba.trading.domain.entity.Instrument;
import com.zkuba.trading.domain.entity.Watchlist;
import com.zkuba.trading.domain.exception.InstrumentNotFoundException;
import com.zkuba.trading.domain.exception.WatchlistNotFoundException;
import com.zkuba.trading.dto.request.CreateWatchlistRequest;
import com.zkuba.trading.dto.response.WatchlistResponse;
import com.zkuba.trading.mapper.WatchlistMapper;
import com.zkuba.trading.repository.InstrumentRepository;
import com.zkuba.trading.repository.WatchlistRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock
    private WatchlistMapper watchlistMapper;

    @Mock
    private InstrumentRepository instruments;

    @Mock
    private WatchlistRepository watchlists;

    @InjectMocks
    private WatchlistService watchlistService;

    private Watchlist userWatchlist;

    @BeforeEach
    void setUp() {
        userWatchlist = new Watchlist();
        userWatchlist.setId(5L);
        userWatchlist.setUserId(1L);
        userWatchlist.setName("Tech Stocks");
    }

    @Test
    @DisplayName("getAll should return list of watchlist responses for user")
    void getAll_Success() {
        when(watchlists.findByUserId(1L)).thenReturn(List.of(userWatchlist));
        when(watchlistMapper.findSymbols(5L)).thenReturn(List.of("AAPL", "NVDA"));

        List<WatchlistResponse> result = watchlistService.getAll(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(5L);
        assertThat(result.get(0).name()).isEqualTo("Tech Stocks");
        assertThat(result.get(0).symbols()).containsExactly("AAPL", "NVDA");
    }

    @Test
    @DisplayName("create should save new watchlist and return response")
    void create_Success() {
        CreateWatchlistRequest request = new CreateWatchlistRequest("Tech Stocks");

        WatchlistResponse response = watchlistService.create(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Tech Stocks");
        assertThat(response.symbols()).isEmpty();

        verify(watchlists).save(any(Watchlist.class));
    }

    @Test
    @DisplayName("addSymbol should add symbol when watchlist belongs to user and symbol exists")
    void addSymbol_Success() {
        Instrument instrument = new Instrument();
        instrument.setSymbol("AAPL");
        instrument.setTradable(true);

        when(watchlists.findById(5L)).thenReturn(Optional.of(userWatchlist));
        when(instruments.findById("AAPL")).thenReturn(Optional.of(instrument));

        watchlistService.addSymbol(1L, 5L, "aapl");

        verify(watchlistMapper).addSymbol(5L, "AAPL");
    }

    @Test
    @DisplayName("addSymbol should throw WatchlistNotFoundException when watchlist does not belong to user")
    void addSymbol_Unauthorized_ThrowsException() {
        Watchlist otherUserWatchlist = new Watchlist();
        otherUserWatchlist.setId(5L);
        otherUserWatchlist.setUserId(99L);

        when(watchlists.findById(5L)).thenReturn(Optional.of(otherUserWatchlist));

        assertThatThrownBy(() -> watchlistService.addSymbol(1L, 5L, "AAPL"))
                .isInstanceOf(WatchlistNotFoundException.class);
    }

    @Test
    @DisplayName("addSymbol should throw InstrumentNotFoundException when instrument does not exist")
    void addSymbol_InstrumentNotFound_ThrowsException() {
        when(watchlists.findById(5L)).thenReturn(Optional.of(userWatchlist));
        when(instruments.findById("INVALID")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> watchlistService.addSymbol(1L, 5L, "INVALID"))
                .isInstanceOf(InstrumentNotFoundException.class);
    }

    @Test
    @DisplayName("removeSymbol should remove symbol when watchlist belongs to user")
    void removeSymbol_Success() {
        when(watchlists.findById(5L)).thenReturn(Optional.of(userWatchlist));

        watchlistService.removeSymbol(1L, 5L, "aapl");

        verify(watchlistMapper).removeSymbol(5L, "AAPL");
    }

    @Test
    @DisplayName("removeSymbol should throw WatchlistNotFoundException when watchlist does not exist")
    void removeSymbol_WatchlistNotFound_ThrowsException() {
        when(watchlists.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> watchlistService.removeSymbol(1L, 5L, "AAPL"))
                .isInstanceOf(WatchlistNotFoundException.class);
    }
}

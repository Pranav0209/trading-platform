package com.zkuba.trading.service;

import com.zkuba.trading.domain.entity.Instrument;
import com.zkuba.trading.exception.InstrumentNotFoundException;
import com.zkuba.trading.repository.InstrumentRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstrumentServiceTest {

    @Mock
    private InstrumentRepository instruments;

    @InjectMocks
    private InstrumentServiceImpl instrumentService;

    private Instrument aapl;

    @BeforeEach
    void setUp() {
        aapl = new Instrument();
        aapl.setSymbol("AAPL");
        aapl.setDisplayName("Apple Inc.");
        aapl.setAssetClass("EQUITY");
        aapl.setCurrency("USD");
        aapl.setTradable(true);
    }

    @Test
    @DisplayName("all should return list of all instruments")
    void all_Success() {
        when(instruments.findAll()).thenReturn(List.of(aapl));

        List<Instrument> result = instrumentService.all();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSymbol()).isEqualTo("AAPL");
    }

    @Test
    @DisplayName("get should return instrument when symbol exists (case-insensitive)")
    void get_Success() {
        when(instruments.findById("AAPL")).thenReturn(Optional.of(aapl));

        Instrument result = instrumentService.get("aapl");

        assertThat(result).isNotNull();
        assertThat(result.getSymbol()).isEqualTo("AAPL");
    }

    @Test
    @DisplayName("get should throw InstrumentNotFoundException when symbol does not exist")
    void get_NotFound_ThrowsException() {
        when(instruments.findById("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> instrumentService.get("unknown"))
                .isInstanceOf(InstrumentNotFoundException.class);
    }
}

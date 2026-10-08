package com.zkuba.trading.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkuba.trading.domain.entity.Account;
import com.zkuba.trading.dto.request.CreateWatchlistRequest;
import com.zkuba.trading.dto.response.WatchlistResponse;
import com.zkuba.trading.exception.GlobalExceptionHandler;
import com.zkuba.trading.security.JwtAuthenticationFilter;
import com.zkuba.trading.service.AccountService;
import com.zkuba.trading.service.MarketDataStreamService;
import com.zkuba.trading.service.WatchlistService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import com.zkuba.trading.security.JwtService;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest(controllers = {WatchlistController.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class WatchlistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WatchlistService watchlistService;

    @MockBean
    private MarketDataStreamService marketDataStreamService;
    
    @MockBean
    private AccountService accountService;

    @MockBean
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtAuthenticationFilter.AuthenticatedUser user = new JwtAuthenticationFilter.AuthenticatedUser(2L, "user@example.com", "ROLE_USER");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void streamPrices_Success() throws Exception {
        Account account = new Account();
        account.setCurrency("EUR");
        when(accountService.getByUserId(2L)).thenReturn(account);
        
        SseEmitter emitter = new SseEmitter();
        when(marketDataStreamService.subscribe(2L, "EUR")).thenReturn(emitter);

        mockMvc.perform(get("/api/v1/watchlists/stream"))
                .andExpect(status().isOk());
    }

    @Test
    void all_Success() throws Exception {
        WatchlistResponse resp = new WatchlistResponse(10L, "Tech", List.of("AAPL"));
        when(watchlistService.getAll(2L)).thenReturn(List.of(resp));

        mockMvc.perform(get("/api/v1/watchlists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10L))
                .andExpect(jsonPath("$[0].name").value("Tech"));
    }

    @Test
    void create_Success() throws Exception {
        CreateWatchlistRequest req = new CreateWatchlistRequest("Tech");
        WatchlistResponse resp = new WatchlistResponse(10L, "Tech", List.of());
        when(watchlistService.create(eq(2L), any(CreateWatchlistRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/v1/watchlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L));
    }

    @Test
    void add_Success() throws Exception {
        mockMvc.perform(post("/api/v1/watchlists/{id}/symbols/{symbol}", 10L, "AAPL"))
                .andExpect(status().isOk());

        verify(watchlistService).addSymbol(2L, 10L, "AAPL");
    }

    @Test
    void delete_Success() throws Exception {
        mockMvc.perform(delete("/api/v1/watchlists/{id}", 10L))
                .andExpect(status().isNoContent());

        verify(watchlistService).delete(2L, 10L);
    }

    @Test
    void remove_Success() throws Exception {
        mockMvc.perform(delete("/api/v1/watchlists/{id}/symbols/{symbol}", 10L, "AAPL"))
                .andExpect(status().isOk());

        verify(watchlistService).removeSymbol(2L, 10L, "AAPL");
    }
}

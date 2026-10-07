package com.zkuba.trading.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.zkuba.trading.domain.entity.Account;
import com.zkuba.trading.domain.entity.RealisedPnl;
import com.zkuba.trading.dto.response.RealisedPnlHistoryResponse;
import com.zkuba.trading.exception.ForbiddenException;
import com.zkuba.trading.exception.InvalidParametersException;
import com.zkuba.trading.repository.AccountRepository;
import com.zkuba.trading.repository.RealisedPnlRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class RealisedPnlServiceImplTest {

    @Mock
    private RealisedPnlRepository realisedPnlRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private RealisedPnlServiceImpl service;

    @BeforeEach
    void setUp() {
        // Mocks are automatically injected by @InjectMocks
    }

    // ==============================================
    // JWT Validation Tests
    // ==============================================

    @Test
    void testJwtValidation_ForbiddenWhenUserIdMismatch() {
        // Given: accountId belongs to userId 100, but JWT claims userId 200
        Long accountId = 1L;
        Long jwtUserId = 200L;
        Account account = new Account();
        account.setId(accountId);
        account.setUserId(100L); // Different user
        
        when(accountRepository.findById(accountId))
                .thenReturn(Optional.of(account));

        // When/Then: Should throw ForbiddenException
        assertThrows(ForbiddenException.class, () ->
                service.getRealisedPnlHistory(accountId, jwtUserId, null, null, null, 0, 20)
        );
    }

    @Test
    void testJwtValidation_AllowsMatchingUserId() {
        // Given: accountId belongs to userId 100, JWT claims userId 100
        Long accountId = 1L;
        Long jwtUserId = 100L;
        Account account = new Account();
        account.setId(accountId);
        account.setUserId(jwtUserId); // Same user
        
        when(accountRepository.findById(accountId))
                .thenReturn(Optional.of(account));
        
        List<RealisedPnl> records = new ArrayList<>();
        Page<RealisedPnl> page = new PageImpl<>(records, PageRequest.of(0, 20), 0);
        
        when(realisedPnlRepository.findRealisedPnlHistory(eq(accountId), any(), any(), any(), any()))
                .thenReturn(page);
        when(realisedPnlRepository.sumRealisedPnl(eq(accountId), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        // When: Calling with matching userId
        RealisedPnlHistoryResponse response = service.getRealisedPnlHistory(
                accountId, jwtUserId, null, null, null, 0, 20
        );

        // Then: Should succeed
        assertNotNull(response);
        assertEquals(accountId, response.accountId());
    }

    // ==============================================
    // Pagination Tests
    // ==============================================

    @Test
    void testPaginationValidation_RejectsSizeZero() {
        Long accountId = 1L;
        Long jwtUserId = 1L;
        Account account = new Account();
        account.setId(accountId);
        account.setUserId(jwtUserId);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        // When/Then: pageSize 0 should be rejected
        assertThrows(InvalidParametersException.class, () ->
                service.getRealisedPnlHistory(accountId, jwtUserId, null, null, null, 0, 0)
        );
    }

    @Test
    void testPaginationValidation_RejectsSizeOver100() {
        Long accountId = 1L;
        Long jwtUserId = 1L;
        Account account = new Account();
        account.setId(accountId);
        account.setUserId(jwtUserId);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        // When/Then: pageSize > 100 should be rejected
        assertThrows(InvalidParametersException.class, () ->
                service.getRealisedPnlHistory(accountId, jwtUserId, null, null, null, 0, 101)
        );
    }

    @Test
    void testPaginationValidation_RejectsNegativePage() {
        Long accountId = 1L;
        Long jwtUserId = 1L;
        Account account = new Account();
        account.setId(accountId);
        account.setUserId(jwtUserId);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        // When/Then: negative page should be rejected
        assertThrows(InvalidParametersException.class, () ->
                service.getRealisedPnlHistory(accountId, jwtUserId, null, null, null, -1, 20)
        );
    }

    @Test
    void testPaginationComputation_ComputesTotalPages() {
        // Given: 45 total records, 20 per page
        Long accountId = 1L;
        Long jwtUserId = 1L;
        Account account = new Account();
        account.setId(accountId);
        account.setUserId(jwtUserId);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        
        List<RealisedPnl> pageRecords = createRealisedPnlRecords(20);
        Page<RealisedPnl> page = new PageImpl<>(pageRecords, PageRequest.of(0, 20), 45);
        
        when(realisedPnlRepository.findRealisedPnlHistory(eq(accountId), any(), any(), any(), any()))
                .thenReturn(page);
        when(realisedPnlRepository.sumRealisedPnl(eq(accountId), any(), any(), any()))
                .thenReturn(new BigDecimal("1000.00"));

        // When: Querying page 0 with size 20
        RealisedPnlHistoryResponse response = service.getRealisedPnlHistory(
                accountId, jwtUserId, null, null, null, 0, 20
        );

        // Then: totalPages should be ceil(45/20) = 3
        assertEquals(3, response.summary().totalPages());
        assertEquals(20, response.records().size());
        assertEquals(0, response.summary().currentPage());
    }

    // ==============================================
    // Date Range Tests
    // ==============================================

    @Test
    void testDateValidation_RejectsFromAfterTo() {
        Long accountId = 1L;
        Long jwtUserId = 1L;
        Account account = new Account();
        account.setId(accountId);
        account.setUserId(jwtUserId);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        LocalDate from = LocalDate.of(2026, 10, 10);
        LocalDate to = LocalDate.of(2026, 10, 9); // Before 'from'

        // When/Then: from > to should be rejected
        assertThrows(InvalidParametersException.class, () ->
                service.getRealisedPnlHistory(accountId, jwtUserId, null, from, to, 0, 20)
        );
    }

    @Test
    void testDateValidation_AcceptsEqualFromTo() {
        Long accountId = 1L;
        Long jwtUserId = 1L;
        LocalDate date = LocalDate.of(2026, 10, 10);
        Account account = new Account();
        account.setId(accountId);
        account.setUserId(jwtUserId);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        
        List<RealisedPnl> records = new ArrayList<>();
        Page<RealisedPnl> page = new PageImpl<>(records, PageRequest.of(0, 20), 0);
        
        when(realisedPnlRepository.findRealisedPnlHistory(eq(accountId), any(), any(), any(), any()))
                .thenReturn(page);
        when(realisedPnlRepository.sumRealisedPnl(eq(accountId), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        // When: from == to
        RealisedPnlHistoryResponse response = service.getRealisedPnlHistory(
                accountId, jwtUserId, null, date, date, 0, 20
        );

        // Then: Should succeed
        assertNotNull(response);
    }

    @Test
    void testDateConversion_InclusiveRanges() {
        Long accountId = 1L;
        Long jwtUserId = 1L;
        LocalDate from = LocalDate.of(2026, 10, 01);
        LocalDate to = LocalDate.of(2026, 10, 31);
        
        Account account = new Account();
        account.setId(accountId);
        account.setUserId(jwtUserId);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        
        List<RealisedPnl> records = new ArrayList<>();
        Page<RealisedPnl> page = new PageImpl<>(records, PageRequest.of(0, 20), 0);
        
        when(realisedPnlRepository.findRealisedPnlHistory(eq(accountId), any(), any(), any(), any()))
                .thenReturn(page);
        when(realisedPnlRepository.sumRealisedPnl(eq(accountId), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        // When: Calling with from/to dates
        service.getRealisedPnlHistory(accountId, jwtUserId, null, from, to, 0, 20);

        // Then: Repository should be called with inclusive OffsetDateTime ranges
        // from = 2026-10-01 00:00:00 UTC
        // to = 2026-10-31 23:59:59.999 UTC
        verify(realisedPnlRepository).findRealisedPnlHistory(
                eq(accountId),
                any(),
                argThat(start -> start.getHour() == 0 && start.getMinute() == 0),
                argThat(end -> end.getHour() == 23 && end.getMinute() == 59),
                any()
        );
    }

    // ==============================================
    // P&L Calculation Tests
    // ==============================================

    @Test
    void testPnlCalculation_ProfitCase() {
        // Given: SELL at 150, averageCost 100, quantity 10
        // Expected P&L: (150 - 100) * 10 = 500
        Long accountId = 1L;
        Long jwtUserId = 1L;
        Account account = new Account();
        account.setId(accountId);
        account.setUserId(jwtUserId);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        
        RealisedPnl pnlRecord = new RealisedPnl();
        pnlRecord.setId(1L);
        pnlRecord.setOrderId(UUID.randomUUID());
        pnlRecord.setAccountId(accountId);
        pnlRecord.setSymbol("AAPL");
        pnlRecord.setQuantity(10);
        pnlRecord.setSellPrice(new BigDecimal("150.00"));
        pnlRecord.setAverageCost(new BigDecimal("100.00"));
        pnlRecord.setRealisedPnl(new BigDecimal("500.00"));
        pnlRecord.setRealisedAt(OffsetDateTime.now(ZoneOffset.UTC));
        
        List<RealisedPnl> records = List.of(pnlRecord);
        Page<RealisedPnl> page = new PageImpl<>(records, PageRequest.of(0, 20), 1);
        
        when(realisedPnlRepository.findRealisedPnlHistory(eq(accountId), any(), any(), any(), any()))
                .thenReturn(page);
        when(realisedPnlRepository.sumRealisedPnl(eq(accountId), any(), any(), any()))
                .thenReturn(new BigDecimal("500.00"));

        // When: Querying P&L
        RealisedPnlHistoryResponse response = service.getRealisedPnlHistory(
                accountId, jwtUserId, null, null, null, 0, 20
        );

        // Then: Record should show profit of 500
        assertEquals(1, response.records().size());
        assertEquals(new BigDecimal("500.00"), response.records().get(0).realisedPnl());
        assertEquals(new BigDecimal("500.00"), response.summary().totalRealisedPnl());
    }

    @Test
    void testPnlCalculation_LossCase() {
        // Given: SELL at 80, averageCost 100, quantity 10
        // Expected P&L: (80 - 100) * 10 = -200
        Long accountId = 1L;
        Long jwtUserId = 1L;
        Account account = new Account();
        account.setId(accountId);
        account.setUserId(jwtUserId);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        
        RealisedPnl pnlRecord = new RealisedPnl();
        pnlRecord.setId(1L);
        pnlRecord.setOrderId(UUID.randomUUID());
        pnlRecord.setAccountId(accountId);
        pnlRecord.setSymbol("AAPL");
        pnlRecord.setQuantity(10);
        pnlRecord.setSellPrice(new BigDecimal("80.00"));
        pnlRecord.setAverageCost(new BigDecimal("100.00"));
        pnlRecord.setRealisedPnl(new BigDecimal("-200.00"));
        pnlRecord.setRealisedAt(OffsetDateTime.now(ZoneOffset.UTC));
        
        List<RealisedPnl> records = List.of(pnlRecord);
        Page<RealisedPnl> page = new PageImpl<>(records, PageRequest.of(0, 20), 1);
        
        when(realisedPnlRepository.findRealisedPnlHistory(eq(accountId), any(), any(), any(), any()))
                .thenReturn(page);
        when(realisedPnlRepository.sumRealisedPnl(eq(accountId), any(), any(), any()))
                .thenReturn(new BigDecimal("-200.00"));

        // When: Querying P&L
        RealisedPnlHistoryResponse response = service.getRealisedPnlHistory(
                accountId, jwtUserId, null, null, null, 0, 20
        );

        // Then: Record should show loss of -200
        assertEquals(1, response.records().size());
        assertEquals(new BigDecimal("-200.00"), response.records().get(0).realisedPnl());
        assertEquals(new BigDecimal("-200.00"), response.summary().totalRealisedPnl());
    }

    @Test
    void testPnlCalculation_BreakEvenCase() {
        // Given: SELL at 100, averageCost 100, quantity 10
        // Expected P&L: (100 - 100) * 10 = 0
        Long accountId = 1L;
        Long jwtUserId = 1L;
        Account account = new Account();
        account.setId(accountId);
        account.setUserId(jwtUserId);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        
        RealisedPnl pnlRecord = new RealisedPnl();
        pnlRecord.setId(1L);
        pnlRecord.setOrderId(UUID.randomUUID());
        pnlRecord.setAccountId(accountId);
        pnlRecord.setSymbol("AAPL");
        pnlRecord.setQuantity(10);
        pnlRecord.setSellPrice(new BigDecimal("100.00"));
        pnlRecord.setAverageCost(new BigDecimal("100.00"));
        pnlRecord.setRealisedPnl(BigDecimal.ZERO);
        pnlRecord.setRealisedAt(OffsetDateTime.now(ZoneOffset.UTC));
        
        List<RealisedPnl> records = List.of(pnlRecord);
        Page<RealisedPnl> page = new PageImpl<>(records, PageRequest.of(0, 20), 1);
        
        when(realisedPnlRepository.findRealisedPnlHistory(eq(accountId), any(), any(), any(), any()))
                .thenReturn(page);
        when(realisedPnlRepository.sumRealisedPnl(eq(accountId), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        // When: Querying P&L
        RealisedPnlHistoryResponse response = service.getRealisedPnlHistory(
                accountId, jwtUserId, null, null, null, 0, 20
        );

        // Then: Record should show zero P&L
        assertEquals(1, response.records().size());
        assertEquals(BigDecimal.ZERO, response.records().get(0).realisedPnl());
        assertEquals(BigDecimal.ZERO, response.summary().totalRealisedPnl());
    }

    // ==============================================
    // Symbol Filtering Tests
    // ==============================================

    @Test
    void testSymbolFiltering_PassesSymbolToRepository() {
        Long accountId = 1L;
        Long jwtUserId = 1L;
        String symbol = "AAPL";
        
        Account account = new Account();
        account.setId(accountId);
        account.setUserId(jwtUserId);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        
        List<RealisedPnl> records = new ArrayList<>();
        Page<RealisedPnl> page = new PageImpl<>(records, PageRequest.of(0, 20), 0);
        
        when(realisedPnlRepository.findRealisedPnlHistory(eq(accountId), any(), any(), any(), any()))
                .thenReturn(page);
        when(realisedPnlRepository.sumRealisedPnl(eq(accountId), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        // When: Querying with symbol filter
        service.getRealisedPnlHistory(accountId, jwtUserId, symbol, null, null, 0, 20);

        // Then: Repository should be called with symbol
        verify(realisedPnlRepository).findRealisedPnlHistory(
                eq(accountId),
                eq(symbol),
                any(),
                any(),
                any()
        );
    }

    @Test
    void testSymbolFiltering_NullWhenNotProvided() {
        Long accountId = 1L;
        Long jwtUserId = 1L;
        
        Account account = new Account();
        account.setId(accountId);
        account.setUserId(jwtUserId);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        
        List<RealisedPnl> records = new ArrayList<>();
        Page<RealisedPnl> page = new PageImpl<>(records, PageRequest.of(0, 20), 0);
        
        when(realisedPnlRepository.findRealisedPnlHistory(eq(accountId), any(), any(), any(), any()))
                .thenReturn(page);
        when(realisedPnlRepository.sumRealisedPnl(eq(accountId), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        // When: Querying without symbol filter
        service.getRealisedPnlHistory(accountId, jwtUserId, null, null, null, 0, 20);

        // Then: Repository should be called with null symbol
        verify(realisedPnlRepository).findRealisedPnlHistory(
                eq(accountId),
                isNull(),
                any(),
                any(),
                any()
        );
    }

    // ==============================================
    // Helper Methods
    // ==============================================

    private List<RealisedPnl> createRealisedPnlRecords(int count) {
        List<RealisedPnl> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            RealisedPnl pnl = new RealisedPnl();
            pnl.setId((long) (i + 1));
            pnl.setOrderId(UUID.randomUUID());
            pnl.setAccountId(1L);
            pnl.setSymbol("AAPL");
            pnl.setQuantity(10);
            pnl.setSellPrice(new BigDecimal("150.00"));
            pnl.setAverageCost(new BigDecimal("100.00"));
            pnl.setRealisedPnl(new BigDecimal("500.00"));
            pnl.setRealisedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(count - i));
            records.add(pnl);
        }
        return records;
    }
}

package com.zkuba.trading.service;

import com.zkuba.trading.domain.entity.Account;
import com.zkuba.trading.domain.entity.RealisedPnl;
import com.zkuba.trading.dto.response.RealisedPnlHistoryResponse;
import com.zkuba.trading.dto.response.RealisedPnlRecord;
import com.zkuba.trading.dto.response.RealisedPnlSummary;
import com.zkuba.trading.exception.ForbiddenException;
import com.zkuba.trading.exception.InvalidParametersException;
import com.zkuba.trading.repository.AccountRepository;
import com.zkuba.trading.repository.RealisedPnlRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RealisedPnlServiceImpl implements RealisedPnlService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    @Autowired
    private RealisedPnlRepository realisedPnlRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Override
    @Transactional(readOnly = true)
    public RealisedPnlHistoryResponse getRealisedPnlHistory(
            Long accountId,
            Long userId,
            String symbol,
            LocalDate from,
            LocalDate to,
            Integer page,
            Integer size) {

        // Validate access: accountId must belong to userId
        validateAccountAccess(accountId, userId);

        // Validate and normalize parameters
        int pageNum = page != null ? page : 0;
        int pageSize = size != null ? size : DEFAULT_PAGE_SIZE;
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new InvalidParametersException("PNL-VAL-001", 
                    "Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (pageNum < 0) {
            throw new InvalidParametersException("PNL-VAL-002", "Page must be >= 0");
        }

        // Convert LocalDate to OffsetDateTime (inclusive ranges in UTC)
        OffsetDateTime fromDateTime = null;
        OffsetDateTime toDateTime = null;
        
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidParametersException("PNL-VAL-003", "from date cannot be after to date");
        }
        
        if (from != null) {
            // Start of day in UTC
            fromDateTime = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        }
        
        if (to != null) {
            // End of day in UTC (23:59:59.999999999)
            toDateTime = to.atTime(23, 59, 59, 999_999_999).atOffset(ZoneOffset.UTC);
        }

        // Query with pagination
        Pageable pageable = PageRequest.of(pageNum, pageSize);
        Page<RealisedPnl> pageResult = realisedPnlRepository.findRealisedPnlHistory(
                accountId, symbol, fromDateTime, toDateTime, pageable);

        // Convert to DTOs
        List<RealisedPnlRecord> records = pageResult.getContent()
                .stream()
                .map(pnl -> new RealisedPnlRecord(
                        pnl.getId(),
                        pnl.getOrderId(),
                        pnl.getSymbol(),
                        pnl.getQuantity(),
                        pnl.getSellPrice(),
                        pnl.getAverageCost(),
                        pnl.getRealisedPnl(),
                        pnl.getRealisedAt()
                ))
                .collect(Collectors.toList());

        // Calculate total realised P&L across ALL records (not just current page)
        BigDecimal totalPnl = realisedPnlRepository.sumRealisedPnl(
                accountId, symbol, fromDateTime, toDateTime);

        // Build summary
        long totalCount = pageResult.getTotalElements();
        long totalPages = totalCount > 0 ? (totalCount + pageSize - 1) / pageSize : 0;
        RealisedPnlSummary summary = new RealisedPnlSummary(
                totalCount,
                totalPages,
                (long) pageNum,
                (long) pageSize,
                totalPnl
        );

        // Build response
        return new RealisedPnlHistoryResponse(
                accountId,
                "USD",  // TODO: Get from account entity if multi-currency support added
                from,
                to,
                records,
                summary,
                OffsetDateTime.now()
        );
    }

    /**
     * Verify that the account belongs to the authenticated user.
     * Throws ForbiddenException (403) if not.
     */
    private void validateAccountAccess(Long accountId, Long userId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new com.zkuba.trading.exception.AccountNotFoundException(accountId));
        
        if (!account.getUserId().equals(userId)) {
            throw new ForbiddenException("PNL-403", "Account does not belong to token user");
        }
    }
}

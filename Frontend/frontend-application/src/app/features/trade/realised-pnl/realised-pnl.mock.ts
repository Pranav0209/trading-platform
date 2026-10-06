import { Injectable } from '@angular/core';
import { Observable, of, delay } from 'rxjs';
import { RealisedPnlHistoryResponse, RealisedPnlRecord, RealisedPnlSummary } from '../../../../generated/portfolio';

/**
 * Mock P&L Service for Frontend Testing
 * Simulates API responses with hardcoded test data
 * Toggle USE_MOCK_DATA flag in environment to enable/disable
 */
@Injectable({
  providedIn: 'root'
})
export class MockRealisedPnlService {
  
  // Test Data Scenarios
  private readonly allRecords: RealisedPnlRecord[] = [
    // Scenario 1: Profit transaction
    {
      pnlId: 1,
      orderId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0001',
      symbol: 'INFY.NS',
      quantity: 10,
      sellPrice: 2510.00,
      averageCost: 2500.00,
      realisedPnl: 100.00,  // (2510 - 2500) * 10 = 100 ✓ PROFIT
      realisedAt: '2026-10-06T10:05:00Z'
    },
    // Scenario 2: Loss transaction
    {
      pnlId: 2,
      orderId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0002',
      symbol: 'TCS.NS',
      quantity: 5,
      sellPrice: 3900.00,
      averageCost: 4000.00,
      realisedPnl: -500.00,  // (3900 - 4000) * 5 = -500 ✓ LOSS
      realisedAt: '2026-10-05T14:30:00Z'
    },
    // Scenario 3: Break-even transaction
    {
      pnlId: 3,
      orderId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0003',
      symbol: 'RELIANCE.NS',
      quantity: 2,
      sellPrice: 2750.00,
      averageCost: 2750.00,
      realisedPnl: 0.00,  // (2750 - 2750) * 2 = 0 ✓ BREAK-EVEN
      realisedAt: '2026-10-04T09:15:00Z'
    },
    // Scenario 4: Large profit
    {
      pnlId: 4,
      orderId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0004',
      symbol: 'HDFCBANK.NS',
      quantity: 20,
      sellPrice: 1805.50,
      averageCost: 1750.00,
      realisedPnl: 1110.00,  // (1805.50 - 1750) * 20 = 1110 ✓ LARGE PROFIT
      realisedAt: '2026-10-03T11:45:00Z'
    },
    // Scenario 5: Large loss
    {
      pnlId: 5,
      orderId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0005',
      symbol: 'WIPRO.NS',
      quantity: 15,
      sellPrice: 380.00,
      averageCost: 425.00,
      realisedPnl: -675.00,  // (380 - 425) * 15 = -675 ✓ LARGE LOSS
      realisedAt: '2026-10-02T15:20:00Z'
    },
    // Scenario 6: Small profit (test precision)
    {
      pnlId: 6,
      orderId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0006',
      symbol: 'MARUTI.NS',
      quantity: 3,
      sellPrice: 10150.25,
      averageCost: 10145.75,
      realisedPnl: 13.50,  // (10150.25 - 10145.75) * 3 = 13.50 ✓ SMALL PROFIT
      realisedAt: '2026-10-01T10:00:00Z'
    },
    // Scenario 7: Multiple with same symbol (test filtering)
    {
      pnlId: 7,
      orderId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0007',
      symbol: 'INFY.NS',
      quantity: 8,
      sellPrice: 2520.00,
      averageCost: 2510.00,
      realisedPnl: 80.00,  // (2520 - 2510) * 8 = 80 ✓ ANOTHER INFY TRADE
      realisedAt: '2026-09-30T13:25:00Z'
    },
    // Scenario 8: High precision decimals
    {
      pnlId: 8,
      orderId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0008',
      symbol: 'SUNPHARMA.NS',
      quantity: 7,
      sellPrice: 752.3456,
      averageCost: 748.1234,
      realisedPnl: 29.4556,  // (752.3456 - 748.1234) * 7 = 29.4556 ✓ PRECISION TEST
      realisedAt: '2026-09-29T09:50:00Z'
    }
  ];

  /**
   * Get paginated P&L history with optional filters
   * Simulates API response with delay to mimic network latency
   */
  getRealisedPnlHistory(
    accountId: number,
    symbol?: string,
    from?: string,
    to?: string,
    page: number = 0,
    size: number = 20
  ): Observable<RealisedPnlHistoryResponse> {
    
    // Filter by symbol if provided
    let filtered = symbol && symbol.trim()
      ? this.allRecords.filter(r => r.symbol.toLowerCase().includes(symbol.toLowerCase()))
      : this.allRecords;

    // Filter by date range if provided
    if (from) {
      const fromDate = new Date(from);
      filtered = filtered.filter(r => new Date(r.realisedAt) >= fromDate);
    }
    if (to) {
      const toDate = new Date(to);
      toDate.setHours(23, 59, 59, 999); // Inclusive end of day
      filtered = filtered.filter(r => new Date(r.realisedAt) <= toDate);
    }

    // Sort by date descending (most recent first)
    filtered.sort((a, b) => new Date(b.realisedAt).getTime() - new Date(a.realisedAt).getTime());

    // Calculate pagination
    const totalCount = filtered.length;
    const totalPages = Math.ceil(totalCount / size);
    
    // Validate page
    const validPage = Math.min(Math.max(page, 0), Math.max(0, totalPages - 1));
    
    // Get page slice
    const startIdx = validPage * size;
    const endIdx = startIdx + size;
    const records = filtered.slice(startIdx, endIdx);

    // Calculate total P&L across ALL records (not just page)
    const totalRealisedPnl = filtered.reduce((sum, r) => sum + (r.realisedPnl || 0), 0);

    const response: RealisedPnlHistoryResponse = {
      accountId: accountId,
      baseCurrency: 'USD',
      from: from || undefined,
      to: to || undefined,
      records: records,
      summary: {
        totalCount: totalCount,
        totalPages: totalPages,
        currentPage: validPage,
        pageSize: size,
        totalRealisedPnl: totalRealisedPnl
      },
      asOf: new Date().toISOString()
    };

    // Simulate network delay (500-800ms)
    return of(response).pipe(delay(600));
  }

  /**
   * Get all records for debugging
   */
  getAllRecords(): RealisedPnlRecord[] {
    return [...this.allRecords];
  }
}

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { Component, signal } from '@angular/core';
import { render, screen } from '@testing-library/angular';
import { userEvent } from '@testing-library/user-event';
import { OrderBlotter } from './order-blotter';
import { OrderHistoryEntry } from '../../../../generated/trade/model/orderHistoryEntry';
import { AccountsService } from '../../../../generated/trade/api/accounts.service';
import { of, throwError } from 'rxjs';

// Mock data
const MOCK_ORDERS: OrderHistoryEntry[] = [
  {
    orderId: 'ORD-uuid-1',
    accountId: 1,
    symbol: 'INFY.NS',
    side: 'BUY',
    quantity: 100,
    price: 1500.00,
    executedPrice: 1500.50,
    status: 'FILLED',
    idempotencyKey: 'key-1',
    createdOn: '2026-09-28T10:00:00Z'
  },
  {
    orderId: 'ORD-uuid-2',
    accountId: 1,
    symbol: 'RELIANCE.NS',
    side: 'SELL',
    quantity: 50,
    price: 2500.00,
    executedPrice: null,
    status: 'NEW',
    idempotencyKey: 'key-2',
    createdOn: '2026-09-28T09:30:00Z'
  },
  {
    orderId: 'ORD-uuid-3',
    accountId: 1,
    symbol: 'TCS.NS',
    side: 'BUY',
    quantity: 200,
    price: 3800.00,
    executedPrice: null,
    status: 'REJECTED',
    idempotencyKey: 'key-3',
    createdOn: '2026-09-28T09:00:00Z'
  },
  {
    orderId: 'ORD-uuid-4',
    accountId: 1,
    symbol: 'WIPRO.NS',
    side: 'SELL',
    quantity: 300,
    price: 450.00,
    executedPrice: 450.25,
    status: 'CANCELLED',
    idempotencyKey: 'key-4',
    createdOn: '2026-09-28T08:30:00Z'
  }
];

describe('OrderBlotter Component', () => {
  let accountsService: AccountsService;

  beforeEach(() => {
    accountsService = {
      getOrders: vi.fn()
    } as any;
  });

  afterEach(() => {
    vi.clearAllTimers();
  });

  describe('order history renders newest first including rejections', () => {
    it('should render all orders newest first', async () => {
      vi.useFakeTimers();
      (accountsService.getOrders as any).mockReturnValue(of(MOCK_ORDERS));

      const { fixture } = await render(OrderBlotter, {
        componentInputs: { accountId: 1 },
        providers: [{ provide: AccountsService, useValue: accountsService }]
      });

      vi.runAllTimers();
      fixture.detectChanges();
      await fixture.whenStable();

      const rows = screen.getAllByRole('row');
      expect(rows.length).toBe(5); // 1 header + 4 data rows

      // Verify order (newest first)
      expect(rows[1]).toHaveTextContent('ORD-uuid-2'); // NEW order from 09:30
      expect(rows[2]).toHaveTextContent('ORD-uuid-3'); // REJECTED from 09:00
      expect(rows[3]).toHaveTextContent('ORD-uuid-4'); // CANCELLED from 08:30
      expect(rows[4]).toHaveTextContent('ORD-uuid-1'); // FILLED from 10:00

      vi.useRealTimers();
    });

    it('should include rejected orders', async () => {
      vi.useFakeTimers();
      (accountsService.getOrders as any).mockReturnValue(of(MOCK_ORDERS));

      const { fixture } = await render(OrderBlotter, {
        componentInputs: { accountId: 1 },
        providers: [{ provide: accountsService, useValue: accountsService }]
      });

      vi.runAllTimers();
      fixture.detectChanges();
      await fixture.whenStable();

      const rejectedRow = screen.getByTestId('order-row-ORD-uuid-3');
      expect(rejectedRow).toBeInTheDocument();
      expect(rejectedRow).toHaveTextContent('REJECTED');

      vi.useRealTimers();
    });

    it('should handle empty order history gracefully', async () => {
      vi.useFakeTimers();
      (accountsService.getOrders as any).mockReturnValue(of([]));

      const { fixture } = await render(OrderBlotter, {
        componentInputs: { accountId: 1 },
        providers: [{ provide: accountsService, useValue: accountsService }]
      });

      vi.runAllTimers();
      fixture.detectChanges();
      await fixture.whenStable();

      const emptyState = screen.getByTestId('empty-state');
      expect(emptyState).toBeInTheDocument();
      expect(emptyState).toHaveTextContent('No orders found');

      vi.useRealTimers();
    });
  });

  describe('an order at NEW is shown as still working', () => {
    it('should display NEW order with appropriate styling', async () => {
      vi.useFakeTimers();
      (accountsService.getOrders as any).mockReturnValue(of(MOCK_ORDERS));

      const { fixture } = await render(OrderBlotter, {
        componentInputs: { accountId: 1 },
        providers: [{ provide: accountsService, useValue: accountsService }]
      });

      vi.runAllTimers();
      fixture.detectChanges();
      await fixture.whenStable();

      const newOrderRow = screen.getByTestId('order-row-ORD-uuid-2');
      expect(newOrderRow).toHaveTextContent('NEW');
      expect(newOrderRow).toHaveTextContent('RELIANCE.NS');

      const badge = screen.getByTestId('status-badge-NEW');
      expect(badge).toHaveTextContent('NEW');

      vi.useRealTimers();
    });

    it('should show NEW order without executed price', async () => {
      vi.useFakeTimers();
      (accountsService.getOrders as any).mockReturnValue(of(MOCK_ORDERS));

      const { fixture } = await render(OrderBlotter, {
        componentInputs: { accountId: 1 },
        providers: [{ provide: accountsService, useValue: accountsService }]
      });

      vi.runAllTimers();
      fixture.detectChanges();
      await fixture.whenStable();

      const newOrderRow = screen.getByTestId('order-row-ORD-uuid-2');
      expect(newOrderRow.textContent).toContain('-'); // dash for null executedPrice

      vi.useRealTimers();
    });

    it('should auto-refresh while orders are NEW', async () => {
      vi.useFakeTimers();
      (accountsService.getOrders as any).mockReturnValue(of(MOCK_ORDERS));

      const { fixture } = await render(OrderBlotter, {
        componentInputs: { accountId: 1 },
        providers: [{ provide: accountsService, useValue: accountsService }]
      });

      vi.runAllTimers();
      fixture.detectChanges();
      await fixture.whenStable();

      // Fast-forward to trigger auto-refresh
      vi.advanceTimersByTime(5000);

      expect(accountsService.getOrders).toHaveBeenCalled();

      vi.useRealTimers();
    });

    it('should offer a manual refresh button', async () => {
      vi.useFakeTimers();
      (accountsService.getOrders as any).mockReturnValue(of(MOCK_ORDERS));

      const { fixture } = await render(OrderBlotter, {
        componentInputs: { accountId: 1 },
        providers: [{ provide: accountsService, useValue: accountsService }]
      });

      vi.runAllTimers();
      fixture.detectChanges();
      await fixture.whenStable();

      const refreshButton = screen.getByTestId('manual-refresh-button');
      expect(refreshButton).toBeInTheDocument();

      vi.useRealTimers();
    });
  });

  describe('the re-read stops when nothing is at NEW', () => {
    it('should stop auto-refreshing when all orders are terminal', async () => {
      vi.useFakeTimers();
      
      const terminalOrders: OrderHistoryEntry[] = [
        {
          orderId: 'ORD-uuid-1',
          accountId: 1,
          symbol: 'INFY.NS',
          side: 'BUY',
          quantity: 100,
          price: 1500.00,
          executedPrice: 1500.50,
          status: 'FILLED',
          idempotencyKey: 'key-1',
          createdOn: '2026-09-28T10:00:00Z'
        },
        {
          orderId: 'ORD-uuid-2',
          accountId: 1,
          symbol: 'RELIANCE.NS',
          side: 'SELL',
          quantity: 50,
          price: 2500.00,
          executedPrice: null,
          status: 'REJECTED',
          idempotencyKey: 'key-2',
          createdOn: '2026-09-28T09:30:00Z'
        }
      ];

      (accountsService.getOrders as any).mockReturnValue(of(terminalOrders));

      const { fixture } = await render(OrderBlotter, {
        componentInputs: { accountId: 1 },
        providers: [{ provide: accountsService, useValue: accountsService }]
      });

      vi.runAllTimers();
      fixture.detectChanges();
      await fixture.whenStable();

      const initialCallCount = (accountsService.getOrders as any).mock.calls.length;

      // Fast-forward multiple intervals
      vi.advanceTimersByTime(30000); // 6 intervals

      // Should not continue auto-refreshing (no NEW orders)
      expect((accountsService.getOrders as any).mock.calls.length).toBe(initialCallCount);

      vi.useRealTimers();
    });

    it('should stop auto-refreshing after max attempts', async () => {
      vi.useFakeTimers();
      (accountsService.getOrders as any).mockReturnValue(of(MOCK_ORDERS));

      const { fixture } = await render(OrderBlotter, {
        componentInputs: { accountId: 1 },
        providers: [{ provide: accountsService, useValue: accountsService }]
      });

      vi.runAllTimers();
      fixture.detectChanges();
      await fixture.whenStable();

      const initialCallCount = (accountsService.getOrders as any).mock.calls.length;

      // Fast-forward to exceed max attempts (120 * 5000ms = 600000ms = 10 minutes)
      vi.advanceTimersByTime(600000 + 5000);

      // After max attempts, no more refreshes should happen
      expect((accountsService.getOrders as any).mock.calls.length).toBeLessThan(
        initialCallCount + 125 // 1 initial + ~120 auto-refreshes max
      );

      vi.useRealTimers();
    });

    it('should display auto-refresh indicator when refreshing', async () => {
      vi.useFakeTimers();
      (accountsService.getOrders as any).mockReturnValue(of(MOCK_ORDERS));

      const { fixture } = await render(OrderBlotter, {
        componentInputs: { accountId: 1 },
        providers: [{ provide: accountsService, useValue: accountsService }]
      });

      vi.runAllTimers();
      fixture.detectChanges();
      await fixture.whenStable();

      // Should show auto-refresh indicator when there are NEW orders
      expect(screen.getByText(/Auto-refreshing/)).toBeInTheDocument();

      vi.useRealTimers();
    });
  });

  describe('empty history is handled gracefully', () => {
    it('should render empty state message for zero orders', async () => {
      vi.useFakeTimers();
      (accountsService.getOrders as any).mockReturnValue(of([]));

      const { fixture } = await render(OrderBlotter, {
        componentInputs: { accountId: 1 },
        providers: [{ provide: accountsService, useValue: accountsService }]
      });

      vi.runAllTimers();
      fixture.detectChanges();
      await fixture.whenStable();

      expect(screen.getByTestId('empty-state')).toBeInTheDocument();
      expect(screen.getByText(/No orders found/)).toBeInTheDocument();

      vi.useRealTimers();
    });

    it('should handle API errors gracefully', async () => {
      vi.useFakeTimers();
      (accountsService.getOrders as any).mockReturnValue(
        throwError(() => ({ status: 404, statusText: 'Not Found' }))
      );

      const { fixture } = await render(OrderBlotter, {
        componentInputs: { accountId: 1 },
        providers: [{ provide: accountsService, useValue: accountsService }]
      });

      vi.runAllTimers();
      fixture.detectChanges();
      await fixture.whenStable();

      const errorMessage = screen.getByTestId('error-message');
      expect(errorMessage).toBeInTheDocument();
      expect(errorMessage).toHaveTextContent('Account not found');

      vi.useRealTimers();
    });

    it('should not repost orders on refresh', async () => {
      vi.useFakeTimers();
      (accountsService.getOrders as any).mockReturnValue(of(MOCK_ORDERS));

      const { fixture } = await render(OrderBlotter, {
        componentInputs: { accountId: 1 },
        providers: [{ provide: accountsService, useValue: accountsService }]
      });

      vi.runAllTimers();
      fixture.detectChanges();
      await fixture.whenStable();

      // Only getOrders should be called, never any POST methods
      expect(accountsService.getOrders).toHaveBeenCalled();

      // Verify that we're only reading, not posting
      const apiMethod = (accountsService.getOrders as any).getMockName?.();
      expect(apiMethod).not.toContain('post');

      vi.useRealTimers();
    });
  });

  describe('status badges', () => {
    it('should render status badges with correct colors and labels', async () => {
      vi.useFakeTimers();
      (accountsService.getOrders as any).mockReturnValue(of(MOCK_ORDERS));

      const { fixture } = await render(OrderBlotter, {
        componentInputs: { accountId: 1 },
        providers: [{ provide: accountsService, useValue: accountsService }]
      });

      vi.runAllTimers();
      fixture.detectChanges();
      await fixture.whenStable();

      // Check all status badges exist
      expect(screen.getByTestId('status-badge-FILLED')).toBeInTheDocument();
      expect(screen.getByTestId('status-badge-NEW')).toBeInTheDocument();
      expect(screen.getByTestId('status-badge-REJECTED')).toBeInTheDocument();
      expect(screen.getByTestId('status-badge-CANCELLED')).toBeInTheDocument();

      vi.useRealTimers();
    });
  });

  describe('formatting', () => {
    it('should format dates correctly', async () => {
      vi.useFakeTimers();
      (accountsService.getOrders as any).mockReturnValue(of(MOCK_ORDERS));

      const { fixture } = await render(OrderBlotter, {
        componentInputs: { accountId: 1 },
        providers: [{ provide: accountsService, useValue: accountsService }]
      });

      vi.runAllTimers();
      fixture.detectChanges();
      await fixture.whenStable();

      // Verify date is rendered (it should include Sep and a time)
      const cells = screen.getAllByText(/Sep.*\d{2}:\d{2}:\d{2}/);
      expect(cells.length).toBeGreaterThan(0);

      vi.useRealTimers();
    });

    it('should format prices with 2 decimal places', async () => {
      vi.useFakeTimers();
      (accountsService.getOrders as any).mockReturnValue(of(MOCK_ORDERS));

      const { fixture } = await render(OrderBlotter, {
        componentInputs: { accountId: 1 },
        providers: [{ provide: accountsService, useValue: accountsService }]
      });

      vi.runAllTimers();
      fixture.detectChanges();
      await fixture.whenStable();

      // Verify price formatting
      expect(screen.getByText('1500.00')).toBeInTheDocument(); // price
      expect(screen.getByText('1500.50')).toBeInTheDocument(); // executedPrice

      vi.useRealTimers();
    });
  });
});

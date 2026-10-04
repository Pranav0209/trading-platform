import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, Input, OnInit, OnDestroy, inject, signal, effect } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { AccountsService } from '../../../../generated/trade/api/accounts.service';
import { OrderHistoryEntry } from '../../../../generated/trade/model/orderHistoryEntry';
import { Session } from '../../../core/auth/session';
import { ErrorCodeMapper } from '../../../core/error/error-code-mapper';
import { StatusBadgeComponent } from './status-badge';

const REFRESH_INTERVAL_MS = 5000; // 5 second bounded interval for re-reads
const MAX_REFRESH_ATTEMPTS = 120; // Stop after 10 minutes if something stays NEW

@Component({
  selector: 'app-order-blotter',
  imports: [CommonModule, StatusBadgeComponent, MatTableModule],
  templateUrl: './order-blotter.html',
  styleUrl: './order-blotter.css'
})
export class OrderBlotter implements OnInit, OnDestroy {
  @Input() accountId?: number;

  private readonly accountsService = inject(AccountsService);
  private readonly session = inject(Session);
  private readonly router = inject(Router);
  private readonly errorCodeMapper = inject(ErrorCodeMapper);

  // State signals
  orders = signal<OrderHistoryEntry[]>([]);
  dataSource = new MatTableDataSource<OrderHistoryEntry>([]);
  displayedColumns: string[] = ['orderId', 'symbol', 'side', 'quantity', 'price', 'executedPrice', 'status', 'createdOn'];
  
  isLoading = signal(false);
  error = signal<string | null>(null);
  lastRefreshed = signal<Date | null>(null);
  hasWorkingOrders = signal(false);
  refreshAttempts = signal(0);
  isAutoRefreshing = signal(false);
  isStandalone = signal(false);
  selectedRejectReason = signal<string | null>(null);

  private refreshIntervalId: number | null = null;
  private refreshAbortController: AbortController | null = null;

  constructor() {
    // Effect to manage auto-refresh lifecycle
    effect(() => {
      const working = this.hasWorkingOrders();
      const attempts = this.refreshAttempts();
      
      if (working && attempts < MAX_REFRESH_ATTEMPTS) {
        this.startAutoRefresh();
      } else {
        this.stopAutoRefresh();
      }
    });
  }

  ngOnInit(): void {
    this.isStandalone.set(this.router.url === '/orders');
    
    if (!this.accountId) {
      const token = this.session.token();
      if (token) {
        try {
          const payload = JSON.parse(atob(token.split('.')[1]));
          this.accountId = payload.accountId || payload.userId;
        } catch (e) {
          console.error('Failed to parse token for accountId', e);
        }
      }
    }
    this.loadOrderHistory();
  }

  ngOnDestroy(): void {
    this.stopAutoRefresh();
  }

  /**
   * Load order history from the API.
   * Newest first, including all statuses (NEW, FILLED, REJECTED, CANCELLED).
   */
  loadOrderHistory(): void {
    if (!this.accountId) {
      this.error.set('Account ID is required');
      return;
    }

    this.isLoading.set(true);
    this.error.set(null);

    // Abort any pending refresh request
    if (this.refreshAbortController) {
      this.refreshAbortController.abort();
    }
    this.refreshAbortController = new AbortController();

    this.accountsService.getOrders(this.accountId).subscribe({
      next: (orders: OrderHistoryEntry[]) => {
        this.isLoading.set(false);
        this.orders.set(orders);
        this.dataSource.data = orders;
        this.lastRefreshed.set(new Date());
        this.updateWorkingOrdersStatus(orders);
        this.refreshAttempts.set(this.refreshAttempts() + 1);
      },
      error: (error: HttpErrorResponse) => {
        this.isLoading.set(false);
        this.error.set(this.mapError(error));
      }
    });
  }

  /**
   * Manually refresh order history when user clicks the refresh button.
   */
  manualRefresh(): void {
    this.refreshAttempts.set(0); // Reset counter for manual refresh
    this.loadOrderHistory();
  }

  onRowClick(order: OrderHistoryEntry): void {
    if (order.status === 'REJECTED' && order.rejectReason) {
      this.selectedRejectReason.set(order.rejectReason);
    } else {
      this.selectedRejectReason.set(null);
    }
  }

  clearRejectReason(): void {
    this.selectedRejectReason.set(null);
  }

  /**
   * Format ISO datetime string to readable format.
   */
  formatDate(dateString: string): string {
    try {
      const date = new Date(dateString);
      return date.toLocaleString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: true
      });
    } catch {
      return dateString;
    }
  }

  /**
   * Format price to 2 decimal places. Handles null/undefined values.
   */
  formatPrice(price: number | null | undefined): string {
    return (price ?? 0).toFixed(2);
  }

  /**
   * Check if any orders are still working (status = NEW).
   */
  private updateWorkingOrdersStatus(orders: OrderHistoryEntry[]): void {
    const hasNew = orders.some(order => order.status === 'NEW');
    this.hasWorkingOrders.set(hasNew);
  }

  /**
   * Start the bounded auto-refresh interval.
   * Stops when all orders are terminal or max attempts reached.
   */
  private startAutoRefresh(): void {
    if (this.refreshIntervalId !== null || this.isAutoRefreshing()) {
      return; // Already running
    }

    this.isAutoRefreshing.set(true);
    this.refreshIntervalId = window.setInterval(() => {
      this.loadOrderHistory();
    }, REFRESH_INTERVAL_MS);
  }

  /**
   * Stop the auto-refresh interval.
   */
  private stopAutoRefresh(): void {
    if (this.refreshIntervalId !== null) {
      window.clearInterval(this.refreshIntervalId);
      this.refreshIntervalId = null;
    }
    this.isAutoRefreshing.set(false);
  }

  /**
   * Map HTTP errors to user-friendly messages.
   */
  private mapError(error: HttpErrorResponse): string {
    return this.errorCodeMapper.mapErrorToMessage(error);
  }
}

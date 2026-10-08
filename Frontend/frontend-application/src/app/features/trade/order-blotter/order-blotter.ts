import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, Input, OnInit, OnDestroy, inject, signal, effect } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { FormsModule } from '@angular/forms';
import { catchError, of } from 'rxjs';
import { AccountsService } from '../../../../generated/trade/api/accounts.service';
import { OrdersService } from '../../../../generated/trade/api/orders.service';
import { OrderHistoryEntry } from '../../../../generated/trade/model/orderHistoryEntry';
import { UpdateOrderRequest } from '../../../../generated/trade/model/updateOrderRequest';
import { Session } from '../../../core/auth/session';
import { DashboardService } from '../../dashboard/dashboard.service';
import { ErrorCodeMapper } from '../../../core/error/error-code-mapper';
import { ToastService } from '../../../core/notifications/toast.service';
import { StatusBadgeComponent } from './status-badge';

const REFRESH_INTERVAL_MS = 5000; // 5 second bounded interval for re-reads
const MAX_REFRESH_ATTEMPTS = 120; // Stop after 10 minutes if something stays NEW

@Component({
  selector: 'app-order-blotter',
  imports: [CommonModule, StatusBadgeComponent, MatTableModule, FormsModule],
  templateUrl: './order-blotter.html',
  styleUrl: './order-blotter.css'
})
export class OrderBlotter implements OnInit, OnDestroy {
  @Input() accountId?: number;

  private readonly accountsService = inject(AccountsService);
  private readonly ordersService = inject(OrdersService);
  private readonly session = inject(Session);
  private readonly dashboardService = inject(DashboardService);
  private readonly router = inject(Router);
  private readonly errorCodeMapper = inject(ErrorCodeMapper);
  private readonly toastService = inject(ToastService);

  // State signals
  orders = signal<OrderHistoryEntry[]>([]);
  dataSource = new MatTableDataSource<OrderHistoryEntry>([]);
  displayedColumns: string[] = ['expand', 'orderId', 'symbol', 'side', 'quantity', 'price', 'executedPrice', 'status', 'createdOn'];
  
  isLoading = signal(false);
  error = signal<string | null>(null);
  lastRefreshed = signal<Date | null>(null);
  hasWorkingOrders = signal(false);
  refreshAttempts = signal(0);
  isAutoRefreshing = signal(false);
  isStandalone = signal(false);
  expandedOrderId = signal<string | null>(null);
  editFormData = signal<{ quantity: number; price: number } | null>(null);
  selectedRejectReason = signal<string | null>(null);
  
  // Filter state signals
  searchSymbol = signal<string>('');
  selectedStatuses = signal<Set<string>>(new Set());

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
        this.updateWorkingOrdersStatus(orders);
        this.applyFilters();
        this.lastRefreshed.set(new Date());
        this.refreshAttempts.set(this.refreshAttempts() + 1);
        this.dashboardService.refreshAccountData(this.accountId);
      },
      error: (error: HttpErrorResponse) => {
        this.isLoading.set(false);
        this.error.set(this.mapError(error));
      }
    });
  }

  /**
   * Apply filters (status and symbol search) to the data source.
   */
  applyFilters(): void {
    const filtered = this.getFilteredOrders();
    this.dataSource.data = filtered;
  }

  /**
   * Get filtered orders based on current status and symbol filters.
   */
  getFilteredOrders(): OrderHistoryEntry[] {
    const selectedStatuses = this.selectedStatuses();
    const searchSymbol = this.searchSymbol().toLowerCase().trim();

    return this.orders().filter(order => {
      // Filter by status
      const statusMatch = selectedStatuses.size === 0 || selectedStatuses.has(order.status || '');
      
      // Filter by symbol (case-insensitive)
      const symbolMatch = !searchSymbol || (order.symbol?.toLowerCase().includes(searchSymbol) ?? false);

      return statusMatch && symbolMatch;
    });
  }

  /**
   * Toggle a status filter on/off.
   */
  toggleStatusFilter(status: string): void {
    const statuses = new Set(this.selectedStatuses());
    if (statuses.has(status)) {
      statuses.delete(status);
    } else {
      statuses.add(status);
    }
    this.selectedStatuses.set(statuses);
    this.applyFilters();
  }

  /**
   * Update search symbol and apply filters.
   */
  onSearchSymbolChange(symbol: string): void {
    this.searchSymbol.set(symbol);
    this.applyFilters();
  }

  /**
   * Clear all filters and reset to default (all statuses selected).
   */
  clearFilters(): void {
    this.searchSymbol.set('');
    this.selectedStatuses.set(new Set());
    this.applyFilters();
  }

  /**
   * Check if a status filter is currently selected.
   */
  isStatusSelected(status: string): boolean {
    return this.selectedStatuses().has(status);
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

  /**
   * Toggle row expansion for inline editing.
   */
  toggleRowExpansion(order: OrderHistoryEntry): void {
    const orderId = this.extractOrderUuid(order.orderId);
    if (this.expandedOrderId() === orderId) {
      this.expandedOrderId.set(null);
      this.editFormData.set(null);
    } else {
      this.expandedOrderId.set(orderId);
      this.editFormData.set({
        quantity: order.quantity,
        price: order.price ?? 0
      });
    }
  }

  /**
   * Check if a row is currently expanded.
   */
  isRowExpanded(order: OrderHistoryEntry): boolean {
    return this.expandedOrderId() === this.extractOrderUuid(order.orderId);
  }

  /**
   * Extract UUID from order ID format (ORD-XXXX format).
   */
  extractOrderUuid(orderId: string): string {
    const parts = orderId.split('-');
    return parts.length > 1 ? parts.slice(1).join('-') : orderId;
  }

  /**
   * Check if order can be edited (only NEW orders).
   */
  canEditOrder(order: OrderHistoryEntry): boolean {
    return order.status === 'NEW' && !this.isMarketOrder(order);
  }

  isMarketOrder(order: OrderHistoryEntry): boolean {
    return order.price == null;
  }

  /**
   * Cancel an order.
   */
  cancelOrder(orderId: string): void {
    if (!this.accountId) return;
    
    try {
      // Extract UUID from order ID (ORD-XXXX format)
      const uuid = this.extractOrderUuid(orderId);
      
      this.ordersService.cancelOrder(uuid)
        .pipe(
          catchError((error: HttpErrorResponse) => {
            const errorMessage = this.errorCodeMapper.mapErrorToMessage(error);
            console.error('Cancel order error:', errorMessage, error);
            this.toastService.error(errorMessage);
            return of(null);
          })
        )
        .subscribe({
          next: (response) => {
            if (response) {
              this.toastService.success(response.message || 'Order cancelled successfully');
              this.expandedOrderId.set(null);
              this.editFormData.set(null);
              this.loadOrderHistory(); // Refresh the list
            }
          }
        });
    } catch (e) {
      console.error('Cancel order exception:', e);
      this.toastService.error('Failed to cancel order');
    }
  }

  /**
   * Update an order with new quantity and price.
   */
  updateOrder(order: OrderHistoryEntry): void {
    if (!this.accountId || !this.editFormData()) return;

    try {
      const formData = this.editFormData()!;
      const uuid = this.extractOrderUuid(order.orderId);
      
      const updateRequest: UpdateOrderRequest = {
        quantity: formData.quantity
      };

      if (!this.isMarketOrder(order)) {
        updateRequest.limitPrice = formData.price;
      }

      this.ordersService.updateOrder(uuid, updateRequest)
        .pipe(
          catchError((error: HttpErrorResponse) => {
            const errorMessage = this.errorCodeMapper.mapErrorToMessage(error);
            console.error('Update order error:', errorMessage, error);
            this.toastService.error(errorMessage);
            return of(null);
          })
        )
        .subscribe({
          next: (response) => {
            if (response) {
              this.toastService.success(response.message || 'Order updated successfully');
              this.expandedOrderId.set(null);
              this.editFormData.set(null);
              this.loadOrderHistory(); // Refresh the list
            }
          }
        });
    } catch (e) {
      console.error('Update order exception:', e);
      this.toastService.error('Failed to update order');
    }
  }
}

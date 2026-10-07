import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { WatchlistAlertService } from '../services/watchlist-alert.service';
import { PriceAlert, CreateAlertRequest } from '../models/watchlist-alert.models';
import { DashboardService } from '../../../features/dashboard/dashboard.service';

@Component({
  selector: 'app-price-alerts',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './price-alerts.html',
  styleUrls: ['./price-alerts.css'],
})
export class PriceAlertsComponent implements OnInit, OnDestroy {
  alerts: PriceAlert[] = [];
  filteredAlerts: PriceAlert[] = [];
  selectedState: string = 'ALL';
  isLoadingAlerts = false;
  isCreatingAlert = false;
  error: string | null = null;
  accountId: number = 0;

  // Create alert form
  newAlertForm = {
    symbol: '',
    direction: 'ABOVE' as 'ABOVE' | 'BELOW',
    threshold: '',
  };

  alertLimit = 100;

  private destroy$ = new Subject<void>();

  accountCurrency: any;

  constructor(
    private watchlistAlertService: WatchlistAlertService,
    private route: ActivatedRoute,
    private cdr: ChangeDetectorRef,
    private dashboardService: DashboardService
  ) {
    this.accountCurrency = this.dashboardService.accountCurrency;
  }

  ngOnInit(): void {
    this.accountId = this.getAccountIdFromToken();
    
    // Pre-fill symbol from query parameter if provided (from watchlist)
    this.route.queryParams.pipe(takeUntil(this.destroy$)).subscribe(params => {
      if (params['symbol']) {
        this.newAlertForm.symbol = params['symbol'].toUpperCase();
      }
    });
    
    this.loadAlerts();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Load all alerts for the account
   */
  loadAlerts(): void {
    this.isLoadingAlerts = true;
    this.error = null;

    this.watchlistAlertService
      .getAlerts(this.accountId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (response) => {
          this.alerts = response.alerts;
          this.alertLimit = response.limit;
          this.filterAlerts();
          this.isLoadingAlerts = false;
          this.cdr.markForCheck();
        },
        error: (err) => {
          setTimeout(() => {
            this.error = `Failed to load alerts: ${err.error?.message || err.message}`;
            this.isLoadingAlerts = false;
            this.cdr.markForCheck();
          });
        },
      });
  }

  /**
   * Filter alerts by selected state
   */
  filterAlerts(): void {
    if (this.selectedState === 'ALL') {
      this.filteredAlerts = [...this.alerts];
    } else {
      this.filteredAlerts = this.alerts.filter((a) => a.state === this.selectedState);
    }
  }

  /**
   * Create a new price alert
   */
  createAlert(): void {
    // Validation
    if (!this.newAlertForm.symbol.trim()) {
      this.error = 'Symbol is required';
      return;
    }

    const threshold = parseFloat(this.newAlertForm.threshold);
    if (isNaN(threshold) || threshold <= 0) {
      this.error = 'Threshold must be a positive number';
      return;
    }

    const activeAlertCount = this.alerts.filter(
      (a) => a.state === 'ACTIVE' || a.state === 'TRIGGERED'
    ).length;
    if (activeAlertCount >= this.alertLimit) {
      this.error = `Alert limit (${this.alertLimit}) reached. Delete some alerts first.`;
      return;
    }

    this.isCreatingAlert = true;
    this.error = null;

    const request: CreateAlertRequest = {
      symbol: this.newAlertForm.symbol.toUpperCase(),
      direction: this.newAlertForm.direction,
      threshold,
    };

    this.watchlistAlertService
      .createAlert(this.accountId, request)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (alert) => {
          this.alerts.push(alert);
          this.filterAlerts();
          this.resetForm();
          this.isCreatingAlert = false;
          this.cdr.markForCheck();
        },
        error: (err) => {
          setTimeout(() => {
            if (err.status === 409) {
              this.error = 'Alert limit exceeded. Delete some alerts first.';
            } else {
              this.error = `Failed to create alert: ${err.error?.message || err.message}`;
            }
            this.isCreatingAlert = false;
            this.cdr.markForCheck();
          });
        },
      });
  }

  /**
   * Delete an alert
   */
  deleteAlert(alert: PriceAlert): void {
    if (!confirm(`Delete alert for ${alert.symbol}?`)) {
      return;
    }

    this.watchlistAlertService
      .deleteAlert(this.accountId, alert.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.alerts = this.alerts.filter((a) => a.id !== alert.id);
          this.filterAlerts();
          this.cdr.markForCheck();
        },
        error: (err) => {
          setTimeout(() => {
            this.error = `Failed to delete alert: ${err.error?.message || err.message}`;
            this.cdr.markForCheck();
          });
        },
      });
  }

  /**
   * Reset a triggered alert back to ACTIVE
   */
  resetAlert(alert: PriceAlert): void {
    if (!confirm(`Reset alert for ${alert.symbol} to ACTIVE?`)) {
      return;
    }

    this.watchlistAlertService
      .resetAlert(this.accountId, alert.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (updatedAlert) => {
          const index = this.alerts.findIndex((a) => a.id === alert.id);
          if (index >= 0) {
            this.alerts[index] = updatedAlert;
            this.filterAlerts();
          }
          this.cdr.markForCheck();
        },
        error: (err) => {
          setTimeout(() => {
            this.error = `Failed to reset alert: ${err.error?.message || err.message}`;
            this.cdr.markForCheck();
          });
        },
      });
  }

  /**
   * Get state badge class
   */
  getStateBadgeClass(state: string): string {
    switch (state) {
      case 'ACTIVE':
        return 'badge bg-success';
      case 'TRIGGERED':
        return 'badge bg-warning';
      case 'DORMANT':
        return 'badge bg-secondary';
      default:
        return 'badge bg-info';
    }
  }

  /**
   * Get delivery status badge class
   */
  getDeliveryBadgeClass(status: string): string {
    switch (status) {
      case 'DELIVERED':
        return 'badge bg-success';
      case 'FAILED':
        return 'badge bg-danger';
      case 'PENDING':
        return 'badge bg-info';
      default:
        return 'badge bg-secondary';
    }
  }

  /**
   * Get direction label
   */
  getDirectionLabel(direction: string): string {
    return direction === 'ABOVE' ? 'Price rises above' : 'Price falls below';
  }

  /**
   * Reset form to initial state
   */
  private resetForm(): void {
    this.newAlertForm = {
      symbol: '',
      direction: 'ABOVE',
      threshold: '',
    };
  }

  /**
   * Extract accountId from JWT token
   */
  private getAccountIdFromToken(): number {
    const token = localStorage.getItem('token');
    if (!token) {
      console.warn('No token found in localStorage');
      return 0;
    }

    try {
      const payload = token.split('.')[1];
      const decoded = JSON.parse(atob(payload));
      const accountId = decoded.accountId || decoded.userId || 0;
      if (!accountId) {
        console.warn('No accountId or userId found in token', decoded);
      }
      return accountId;
    } catch (e) {
      console.error('Failed to decode token', e);
      return 0;
    }
  }

  /**
   * Get progress bar width for alert count
   */
  getAlertCountProgress(): number {
    const activeCount = this.alerts.filter(
      (a) => a.state === 'ACTIVE' || a.state === 'TRIGGERED'
    ).length;
    return (activeCount / this.alertLimit) * 100;
  }

  /**
   * Format date for display
   */
  formatDate(dateStr: string | null): string {
    if (!dateStr) return 'N/A';
    return new Date(dateStr).toLocaleString();
  }
}

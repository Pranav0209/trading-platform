import { Component, inject, OnInit, signal, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Session } from '../../../core/auth/session';
import { PLService } from '../../../../generated/portfolio/api/pL.service';
import { RealisedPnlHistoryResponse } from '../../../../generated/portfolio/model/realisedPnlHistoryResponse';
import { RealisedPnlRecord } from '../../../../generated/portfolio/model/realisedPnlRecord';

@Component({
  selector: 'app-realised-pnl',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './realised-pnl.html',
  styleUrls: ['./realised-pnl.css']
})
export class RealisedPnlComponent implements OnInit {
  private router = inject(Router);
  private pnlService = inject(PLService);
  private session = inject(Session);
  
  @Input() accountId: number | null = null;
  @Input() title = 'Realised P&L History';
  @Input() showViewAll = false;
  
  // Standalone page headers
  pageTitle = signal<string>('Your Realised P&L');
  pageSubtitle = signal<string>('Transaction-by-transaction history of realised profit and loss.');
  isStandalone = signal<boolean>(false);
  
  // Data state
  pnlRecords = signal<RealisedPnlRecord[]>([]);
  totalCount = signal<number>(0);
  totalPages = signal<number>(0);
  totalRealisedPnl = signal<number>(0);
  baseCurrency = signal<string>('USD');
  
  // Pagination and filtering
  currentPage = signal<number>(0);
  pageSize = signal<number>(20);
  symbol = signal<string>('');
  fromDate = signal<string>('');
  toDate = signal<string>('');
  
  // Loading state
  loading = signal<boolean>(false);
  error = signal<string>('');

  ngOnInit() {
    const url = this.router.url;
    this.isStandalone.set(url === '/pnl' || url === '/realised-pnl');
    
    // Get account ID from Input, or extract from JWT token
    if (!this.accountId || this.accountId <= 0) {
      // Try to get account ID from JWT token
      const token = this.session.token();
      if (token) {
        try {
          const payload = JSON.parse(atob(token.split('.')[1]));
          const tokenAccountId = Number(payload.accountId);
          if (Number.isInteger(tokenAccountId) && tokenAccountId > 0) {
            this.accountId = tokenAccountId;
          }
        } catch (e) {
          console.error('Failed to parse JWT token for account ID:', e);
        }
      }
      
      // Fallback to sessionStorage if token parsing failed
      if (!this.accountId || this.accountId <= 0) {
        const storedAccountId = sessionStorage.getItem('selectedAccountId');
        if (storedAccountId) {
          this.accountId = parseInt(storedAccountId, 10);
        }
      }
    }
    
    // Load data if accountId is available
    if (this.accountId && this.accountId > 0) {
      this.loadPnlHistory();
    }
  }

  /**
   * Load P&L history from the API
   */
  loadPnlHistory() {
    if (!this.accountId || this.accountId <= 0) {
      this.error.set('Account ID is required');
      return;
    }

    this.loading.set(true);
    this.error.set('');

    const params: any = {
      page: this.currentPage(),
      size: this.pageSize()
    };

    if (this.symbol() && this.symbol().trim()) {
      params.symbol = this.symbol().trim();
    }

    if (this.fromDate() && this.fromDate().trim()) {
      params.from = this.fromDate().trim();
    }

    if (this.toDate() && this.toDate().trim()) {
      params.to = this.toDate().trim();
    }

    this.pnlService.getRealisedPnlHistory(
      this.accountId as number,
      params.symbol,
      params.from,
      params.to,
      params.page,
      params.size
    ).subscribe({
      next: (response: RealisedPnlHistoryResponse) => {
        this.pnlRecords.set(response.records || []);
        this.totalCount.set(response.summary?.totalCount || 0);
        this.totalPages.set(response.summary?.totalPages || 0);
        this.totalRealisedPnl.set(response.summary?.totalRealisedPnl || 0);
        this.baseCurrency.set(response.baseCurrency || 'USD');
        this.loading.set(false);
      },
      error: (err: any) => {
        console.error('Error loading P&L history:', err);
        this.error.set('Failed to load P&L history. Please try again.');
        this.loading.set(false);
      }
    });
  }

  /**
   * Navigate to a specific page
   */
  goToPage(page: number) {
    if (page >= 0 && page < this.totalPages()) {
      this.currentPage.set(page);
      this.loadPnlHistory();
    }
  }

  /**
   * Go to next page
   */
  nextPage() {
    this.goToPage(this.currentPage() + 1);
  }

  /**
   * Go to previous page
   */
  previousPage() {
    this.goToPage(this.currentPage() - 1);
  }

  /**
   * Reset filters and reload
   */
  resetFilters() {
    this.symbol.set('');
    this.fromDate.set('');
    this.toDate.set('');
    this.currentPage.set(0);
    this.loadPnlHistory();
  }

  /**
   * Apply filters and reload
   */
  applyFilters() {
    this.currentPage.set(0);
    this.loadPnlHistory();
  }

  /**
   * Format currency value for display
   */
  formatCurrency(value: number | undefined): string {
    if (value === undefined || value === null) {
      return '0.00';
    }
    return value.toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    });
  }

  /**
   * Get CSS class for P&L value (positive/negative)
   */
  getPnlClass(pnl: number | undefined): string {
    if (pnl === undefined || pnl === null) {
      return '';
    }
    return pnl > 0 ? 'positive' : pnl < 0 ? 'negative' : '';
  }

  /**
   * Get sign prefix for P&L value
   */
  getPnlPrefix(pnl: number | undefined): string {
    if (pnl === undefined || pnl === null || pnl === 0) {
      return '';
    }
    return pnl > 0 ? '+' : '';
  }

  /**
   * Calculate page numbers for pagination display
   */
  get pageNumbers(): number[] {
    const pages: number[] = [];
    const maxVisible = 5;
    const total = this.totalPages();
    const current = this.currentPage();

    if (total <= maxVisible) {
      for (let i = 0; i < total; i++) {
        pages.push(i);
      }
    } else {
      const startPage = Math.max(0, current - Math.floor(maxVisible / 2));
      const endPage = Math.min(total, startPage + maxVisible);
      for (let i = startPage; i < endPage; i++) {
        pages.push(i);
      }
    }

    return pages;
  }

  /**
   * Check if there are no records to display
   */
  get hasNoRecords(): boolean {
    return this.totalCount() === 0 && !this.loading();
  }
}

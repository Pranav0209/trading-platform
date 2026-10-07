import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { WatchlistAlertService } from '../services/watchlist-alert.service';
import { Watchlist, WatchlistDetails, WatchlistInstrument } from '../models/watchlist-alert.models';

interface UIWatchlist extends Watchlist {
  isEditing?: boolean;
  newInstrumentSymbol?: string;
}

@Component({
  selector: 'app-watchlist',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './watchlist.html',
  styleUrls: ['./watchlist.css'],
})
export class WatchlistComponent implements OnInit, OnDestroy {
  watchlists: UIWatchlist[] = [];
  selectedWatchlist: WatchlistDetails | null = null;
  newWatchlistName = '';
  isCreatingWatchlist = false;
  isLoadingWatchlists = false;
  isLoadingDetails = false;
  error: string | null = null;
  accountId: number = 0;

  private destroy$ = new Subject<void>();

  constructor(private watchlistService: WatchlistAlertService) {}

  ngOnInit(): void {
    this.accountId = this.getAccountIdFromToken();
    this.loadWatchlists();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Load all watchlists for the account
   */
  loadWatchlists(): void {
    this.isLoadingWatchlists = true;
    this.error = null;

    this.watchlistService
      .getWatchlists(this.accountId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (response) => {
          this.watchlists = response.watchlists;
          this.isLoadingWatchlists = false;
        },
        error: (err) => {
          this.error = `Failed to load watchlists: ${err.error?.message || err.message}`;
          this.isLoadingWatchlists = false;
        },
      });
  }

  /**
   * Load a specific watchlist with live prices
   */
  loadWatchlistDetails(watchlist: Watchlist): void {
    this.isLoadingDetails = true;

    this.watchlistService
      .getWatchlist(this.accountId, watchlist.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (details) => {
          this.selectedWatchlist = details;
          this.isLoadingDetails = false;
        },
        error: (err) => {
          this.error = `Failed to load watchlist: ${err.error?.message || err.message}`;
          this.isLoadingDetails = false;
        },
      });
  }

  /**
   * Create a new watchlist
   */
  createWatchlist(): void {
    if (!this.newWatchlistName.trim()) {
      this.error = 'Watchlist name is required';
      return;
    }

    this.isCreatingWatchlist = true;
    this.error = null;

    this.watchlistService
      .createWatchlist(this.accountId, { name: this.newWatchlistName })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (watchlist) => {
          this.watchlists.push(watchlist);
          this.newWatchlistName = '';
          this.isCreatingWatchlist = false;
        },
        error: (err) => {
          this.error = `Failed to create watchlist: ${err.error?.message || err.message}`;
          this.isCreatingWatchlist = false;
        },
      });
  }

  /**
   * Delete a watchlist
   */
  deleteWatchlist(watchlist: Watchlist): void {
    if (!confirm(`Delete watchlist "${watchlist.name}"?`)) {
      return;
    }

    this.watchlistService
      .deleteWatchlist(this.accountId, watchlist.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.watchlists = this.watchlists.filter((w) => w.id !== watchlist.id);
          if (this.selectedWatchlist?.id === watchlist.id) {
            this.selectedWatchlist = null;
          }
        },
        error: (err) => {
          this.error = `Failed to delete watchlist: ${err.error?.message || err.message}`;
        },
      });
  }

  /**
   * Add an instrument to a watchlist
   */
  addInstrument(watchlist: UIWatchlist): void {
    const symbol = watchlist.newInstrumentSymbol?.toUpperCase().trim();

    if (!symbol) {
      this.error = 'Symbol is required';
      return;
    }

    if (!this.selectedWatchlist) {
      this.error = 'No watchlist selected';
      return;
    }

    this.watchlistService
      .addInstrumentToWatchlist(this.accountId, watchlist.id, symbol)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          watchlist.newInstrumentSymbol = '';
          // Reload watchlist details to get updated instruments and prices
          this.loadWatchlistDetails(this.selectedWatchlist!);
        },
        error: (err) => {
          this.error = `Failed to add instrument: ${err.error?.message || err.message}`;
        },
      });
  }

  /**
   * Remove an instrument from a watchlist
   */
  removeInstrument(watchlistId: number, symbol: string): void {
    if (!confirm(`Remove ${symbol} from watchlist?`)) {
      return;
    }

    this.watchlistService
      .removeInstrumentFromWatchlist(this.accountId, watchlistId, symbol)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          if (this.selectedWatchlist) {
            this.selectedWatchlist.instruments = this.selectedWatchlist.instruments.filter(
              (i) => i.symbol !== symbol
            );
          }
        },
        error: (err) => {
          this.error = `Failed to remove instrument: ${err.error?.message || err.message}`;
        },
      });
  }

  /**
   * Format price change for display
   */
  formatChange(change: number | null, changePercent: number | null): string {
    if (change === null || changePercent === null) return 'N/A';
    const sign = change >= 0 ? '+' : '';
    return `${sign}${change.toFixed(2)} (${(changePercent * 100).toFixed(2)}%)`;
  }

  /**
   * Get color class for price change
   */
  getChangeClass(change: number | null): string {
    if (change === null) return '';
    if (change > 0) return 'text-success';
    if (change < 0) return 'text-danger';
    return 'text-muted';
  }

  /**
   * Extract accountId from JWT token (from localStorage)
   */
  private getAccountIdFromToken(): number {
    const token = localStorage.getItem('authToken');
    if (!token) return 0;

    try {
      const payload = token.split('.')[1];
      const decoded = JSON.parse(atob(payload));
      return decoded.accountId || 0;
    } catch (e) {
      console.error('Failed to decode token', e);
      return 0;
    }
  }

  /**
   * Check if watchlist is empty
   */
  isWatchlistEmpty(watchlist: WatchlistDetails | null): boolean {
    return !watchlist || watchlist.instruments.length === 0;
  }

  /**
   * Select or toggle watchlist view
   */
  toggleWatchlist(watchlist: Watchlist): void {
    if (this.selectedWatchlist?.id === watchlist.id) {
      this.selectedWatchlist = null;
    } else {
      this.loadWatchlistDetails(watchlist);
    }
  }
}

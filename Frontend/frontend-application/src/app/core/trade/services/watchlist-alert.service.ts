import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  Watchlist,
  WatchlistDetails,
  CreateWatchlistRequest,
  PriceAlert,
  CreateAlertRequest,
  AlertsResponse,
} from '../models/watchlist-alert.models';

@Injectable({
  providedIn: 'root',
})
export class WatchlistAlertService {
  private apiUrl = `${environment.tradeApiUrl}/api/v1`;

  constructor(private http: HttpClient) {}

  // ============== WATCHLIST APIs ==============

  /**
   * Get all watchlists for an account
   */
  getWatchlists(accountId: number): Observable<{ watchlists: Watchlist[] }> {
    return this.http.get<{ watchlists: Watchlist[] }>(
      `${this.apiUrl}/accounts/${accountId}/watchlists`
    );
  }

  /**
   * Get a specific watchlist with live prices
   */
  getWatchlist(accountId: number, watchlistId: number): Observable<WatchlistDetails> {
    return this.http.get<WatchlistDetails>(
      `${this.apiUrl}/accounts/${accountId}/watchlists/${watchlistId}`
    );
  }

  /**
   * Create a new watchlist
   */
  createWatchlist(
    accountId: number,
    request: CreateWatchlistRequest
  ): Observable<Watchlist> {
    return this.http.post<Watchlist>(
      `${this.apiUrl}/accounts/${accountId}/watchlists`,
      request
    );
  }

  /**
   * Delete a watchlist
   */
  deleteWatchlist(accountId: number, watchlistId: number): Observable<void> {
    return this.http.delete<void>(
      `${this.apiUrl}/accounts/${accountId}/watchlists/${watchlistId}`
    );
  }

  /**
   * Add an instrument to a watchlist
   */
  addInstrumentToWatchlist(
    accountId: number,
    watchlistId: number,
    symbol: string
  ): Observable<void> {
    return this.http.post<void>(
      `${this.apiUrl}/accounts/${accountId}/watchlists/${watchlistId}/instruments/${symbol}`,
      {}
    );
  }

  /**
   * Remove an instrument from a watchlist
   */
  removeInstrumentFromWatchlist(
    accountId: number,
    watchlistId: number,
    symbol: string
  ): Observable<void> {
    return this.http.delete<void>(
      `${this.apiUrl}/accounts/${accountId}/watchlists/${watchlistId}/instruments/${symbol}`
    );
  }

  // ============== PRICE ALERT APIs ==============

  /**
   * Get all alerts for an account, optionally filtered by state
   */
  getAlerts(accountId: number, state?: string): Observable<AlertsResponse> {
    let url = `${this.apiUrl}/accounts/${accountId}/alerts`;
    if (state) {
      url += `?state=${state}`;
    }
    return this.http.get<AlertsResponse>(url);
  }

  /**
   * Get a specific alert
   */
  getAlert(accountId: number, alertId: number): Observable<PriceAlert> {
    return this.http.get<PriceAlert>(
      `${this.apiUrl}/accounts/${accountId}/alerts/${alertId}`
    );
  }

  /**
   * Create a new price alert
   */
  createAlert(
    accountId: number,
    request: CreateAlertRequest
  ): Observable<PriceAlert> {
    return this.http.post<PriceAlert>(
      `${this.apiUrl}/accounts/${accountId}/alerts`,
      request
    );
  }

  /**
   * Delete (cancel) a price alert
   */
  deleteAlert(accountId: number, alertId: number): Observable<void> {
    return this.http.delete<void>(
      `${this.apiUrl}/accounts/${accountId}/alerts/${alertId}`
    );
  }

  /**
   * Reset a triggered alert back to ACTIVE
   */
  resetAlert(accountId: number, alertId: number): Observable<PriceAlert> {
    return this.http.post<PriceAlert>(
      `${this.apiUrl}/accounts/${accountId}/alerts/${alertId}/reset`,
      {}
    );
  }
}

import { Injectable, inject, signal } from '@angular/core';
import { PortfolioSummary, Holding } from './models';
import { AccountsService } from '../../../generated/trade/api/accounts.service';
import { Session } from '../../core/auth/session';


@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  private accountsService = inject(AccountsService);
  private session = inject(Session);
  
  // Signals
  portfolioSummary = signal<PortfolioSummary>({
    portfolioValue: 0,
    todayPnL: 0,
    todayPnLPercent: 0,
    availableBalance: 0
  });
  holdings = signal<Holding[]>([]);
  userName = signal<string>('User');
  accountStatus = signal<string | null>(null);
  accountCurrency = signal<string>('USD');
  

  constructor() {
    // Rely on LayoutComponent to trigger loadData when authenticated
  }

  clearData() {
    this.portfolioSummary.set({
      portfolioValue: 0,
      todayPnL: 0,
      todayPnLPercent: 0,
      availableBalance: 0
    });
    this.holdings.set([]);
    this.userName.set('User');
    this.accountStatus.set(null);
    this.accountCurrency.set('USD');
  }

  loadData() {
    const token = this.session.token();
    if (!token) return;

    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      // The JWT contains accountId when an account has been provisioned.
      // If it's missing, the account hasn't been created by the admin yet.
      const accountId = payload.accountId;


      if (accountId) {
        // Fetch Account Status
        this.accountsService.getAccount(accountId).subscribe({
          next: (res) => {
            this.accountStatus.set(res.status);
          },
          error: (err) => {
            console.error('Failed to load account', err);
            if (err.status === 403) {
              this.accountStatus.set('SUSPENDED');
            } else {
              this.accountStatus.set('ACTIVE'); // fallback
            }
          }
        });

        // Fetch Balance
        this.accountsService.getBalance(accountId).subscribe({
          next: (res) => {
            this.accountCurrency.set(res.currency || 'USD');
            this.portfolioSummary.set({
              portfolioValue: res.cashBalance, // In real life, add position values
              todayPnL: 0, // Mocked for now
              todayPnLPercent: 0, // Mocked for now
              availableBalance: res.cashBalance
            });
          },
          error: (err) => {
            console.error('Failed to load balance', err);
          }
        });
        // Fetch Positions for Holdings view
        this.accountsService.getPositions(accountId).subscribe({
          next: (res) => {
            // Map generated PositionResponse to our UI Position model
            const mappedPositions = res.map(p => ({
              symbol: p.symbol,
              quantity: p.quantity,
              averagePrice: p.averageCost,
              ltp: p.averageCost, // Mock LTP as averageCost for now
              pnl: 0 // Mock P&L for now
            }));
            // Treat holdings same as positions for the UI mock
            this.holdings.set(mappedPositions);
          },
          error: (err) => {
            console.error('Failed to load holdings', err);
          }
        });
      } else {
        // No accountId in JWT means the account isn't provisioned yet
        this.accountStatus.set('PENDING');
      }
    } catch (e) {
      console.error('Failed to parse token for dashboard data', e);
    }
  }
}

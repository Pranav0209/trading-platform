import { Injectable, inject, signal } from '@angular/core';
import { PortfolioSummary, Holding } from './models';
import { AccountsService } from '../../../generated/trade/api/accounts.service';
import { Session } from '../auth/session';

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
  


  constructor() {
    this.loadData();
  }

  loadData() {
    const token = this.session.token();
    if (!token) return;

    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      // The JWT contains accountId (Sprint 8) or userId (Sprint 6 fallback)
      const accountId = payload.accountId || payload.userId;
      
      // 'sub' is usually the username in standard JWTs
      if (payload.sub) {
        this.userName.set(payload.sub);
      }

      if (accountId) {
        // Fetch Balance
        this.accountsService.getBalance(accountId).subscribe({
          next: (res) => {
            this.portfolioSummary.set({
              portfolioValue: res.cashBalance, // In real life, add position values
              todayPnL: 0, // Mocked for now
              todayPnLPercent: 0, // Mocked for now
              availableBalance: res.cashBalance
            });
          },
          error: (err) => console.error('Failed to load balance', err)
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
          error: (err) => console.error('Failed to load holdings', err)
        });
      }
    } catch (e) {
      console.error('Failed to parse token for dashboard data', e);
    }
  }
}

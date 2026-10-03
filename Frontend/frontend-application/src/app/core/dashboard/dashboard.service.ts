import { Injectable, inject, signal } from '@angular/core';
import { PortfolioSummary, Holding } from './models';
import { AccountsService } from '../../../generated/trade/api/accounts.service';
import { Session } from '../auth/session';

// Demo data for testing without backend
const DEMO_PORTFOLIO: PortfolioSummary = {
  portfolioValue: 125450.50,
  todayPnL: 2450.75,
  todayPnLPercent: 1.98,
  availableBalance: 42500.00
};

const DEMO_HOLDINGS: Holding[] = [
  { symbol: 'INFY', quantity: 25, averagePrice: 1850.25, ltp: 1920.00, pnl: 1748.75 },
  { symbol: 'TCS', quantity: 10, averagePrice: 3750.00, ltp: 3895.50, pnl: 1455.00 },
  { symbol: 'HDFC', quantity: 5, averagePrice: 2650.00, ltp: 2740.00, pnl: 450.00 },
  { symbol: 'ICICI', quantity: 15, averagePrice: 920.50, ltp: 945.75, pnl: 378.75 },
  { symbol: 'RELIANCE', quantity: 8, averagePrice: 2480.00, ltp: 2510.25, pnl: 241.00 },
];

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

      // Check if this is a demo token (demo signature)
      const isDemoToken = token.endsWith('demo_signature');
      
      if (isDemoToken) {
        // Use demo data for demo login
        console.log('Loading demo data for testing UI');
        this.portfolioSummary.set(DEMO_PORTFOLIO);
        this.holdings.set(DEMO_HOLDINGS);
        return;
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
          error: (err) => {
            console.error('Failed to load balance', err);
            // Fallback to demo data on error
            this.portfolioSummary.set(DEMO_PORTFOLIO);
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
            // Fallback to demo data on error
            this.holdings.set(DEMO_HOLDINGS);
          }
        });
      }
    } catch (e) {
      console.error('Failed to parse token for dashboard data', e);
      // Fallback to demo data on parse error
      this.portfolioSummary.set(DEMO_PORTFOLIO);
      this.holdings.set(DEMO_HOLDINGS);
    }
  }
}

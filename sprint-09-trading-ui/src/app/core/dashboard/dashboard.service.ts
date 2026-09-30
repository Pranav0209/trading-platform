import { Injectable, inject, signal } from '@angular/core';
import { PortfolioSummary, Holding, Position, Order, SystemStatus } from './models';
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
  positions = signal<Position[]>([]);
  recentOrders = signal<Order[]>([]);
  userName = signal<string>('User');
  
  // Hardcoded for now until health check APIs are built
  systemStatuses = signal<SystemStatus[]>([
    { service: 'Trade API', status: 'Operational' },
    { service: 'Auth Service', status: 'Operational' },
    { service: 'Order Executor', status: 'Operational' },
    { service: 'Database', status: 'Operational' }
  ]);

  constructor() {
    this.loadData();
  }

  loadData() {
    const token = this.session.token();
    if (!token) return;

    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      // We assume the JWT contains 'userId' or 'sub' which acts as accountId
      const accountId = payload.userId || payload.accountId || 1; // fallback if neither exists
      
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

        // Fetch Positions
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
            this.positions.set(mappedPositions);
            // Treat holdings same as positions for the UI mock
            this.holdings.set(mappedPositions);
          },
          error: (err) => console.error('Failed to load positions', err)
        });

        // Fetch Orders
        this.accountsService.getOrders(accountId).subscribe({
          next: (res) => {
            // Map generated OrderHistoryEntry to our UI Order model
            const mappedOrders = res.map(o => ({
              id: o.orderId,
              time: new Date(o.createdOn).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
              symbol: o.symbol,
              side: o.side as 'BUY' | 'SELL',
              quantity: o.quantity,
              price: o.price,
              status: o.status as 'FILLED' | 'PENDING' | 'REJECTED'
            }));
            this.recentOrders.set(mappedOrders);
          },
          error: (err) => console.error('Failed to load orders', err)
        });
      }
    } catch (e) {
      console.error('Failed to parse token for dashboard data', e);
    }
  }
}

import { Injectable, inject, signal, computed } from '@angular/core';
import { PortfolioSummary, Holding } from './models';
import { AccountsService } from '../../../generated/trade/api/accounts.service';
import { ProfileService } from '../../../generated/auth/api/profile.service';
import { PositionResponse } from '../../../generated/trade/model/positionResponse';
import { Session } from '../../core/auth/session';
import { MarketDataService } from '../../core/services/market-data.service';

@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  private readonly accountsService = inject(AccountsService);
  private readonly profileService = inject(ProfileService);
  private readonly session = inject(Session);
  private readonly marketData = inject(MarketDataService);
  
  // Base State
  private basePositions = signal<PositionResponse[]>([]);
  private baseBalance = signal<number>(0);
  
  userName = signal<string>('User');
  userEmail = signal<string | null>(null);
  accountStatus = signal<string | null>(null);
  accountCurrency = signal<string>('USD');

  // Computed Real-Time State
  holdings = computed(() => {
    const quotes = this.marketData.quotes();
    return this.basePositions().map(p => {
      const quote = quotes[p.symbol];
      const ltp = quote ? quote.price : p.averageCost;
      const pnl = (ltp - p.averageCost) * p.quantity;
      return {
        symbol: p.symbol,
        quantity: p.quantity,
        averagePrice: p.averageCost,
        ltp: ltp,
        pnl: pnl
      };
    });
  });

  portfolioSummary = computed(() => {
    let totalValue = this.baseBalance();
    let totalPnl = 0;
    
    this.holdings().forEach(h => {
       totalValue += h.ltp * h.quantity;
       totalPnl += h.pnl;
    });

    const previousValue = totalValue - totalPnl;
    const pnlPercent = previousValue > 0 ? (totalPnl / previousValue) * 100 : 0;

    return {
      portfolioValue: totalValue,
      todayPnL: totalPnl,
      todayPnLPercent: Number(pnlPercent.toFixed(2)),
      availableBalance: this.baseBalance()
    };
  });
  

  constructor() {
    // Rely on LayoutComponent to trigger loadData when authenticated
  }

  clearData() {
    this.baseBalance.set(0);
    this.basePositions.set([]);
    this.userName.set('User');
    this.userEmail.set(null);
    this.accountStatus.set(null);
    this.accountCurrency.set('USD');
  }

  loadData() {
    const token = this.session.token();
    if (!token) return;

    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      // Fetch the authenticated user's profile
      // This endpoint is specified in the auth contract and should be called after login
      this.profileService.getCurrentUser().subscribe({
        next: (user) => {
          this.userName.set(user.username || 'User');
          this.userEmail.set(user.email || null);
        },
        error: (err) => {
          console.error('Failed to load current user profile', err);
          // Fallback to JWT username if profile endpoint fails
          this.userName.set(payload.sub || 'User');
        }
      });

      this.refreshAccountData(payload.accountId);
    } catch (e) {
      console.error('Failed to parse token for dashboard data', e);
    }
  }

  refreshAccountData(accountIdFromToken?: unknown) {
    const accountId = this.resolveAccountId(accountIdFromToken);

    if (accountId === null) {
      this.resetPortfolioData();
      this.accountStatus.set('PENDING');
      return;
    }

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
          this.accountStatus.set('ACTIVE');
        }
      }
    });

    // Fetch Balance
    this.accountsService.getBalance(accountId).subscribe({
      next: (res) => {
        this.accountCurrency.set(res.currency || 'USD');
        this.baseBalance.set(res.cashBalance);
      },
      error: (err) => {
        console.error('Failed to load balance', err);
      }
    });

    // Fetch Positions for Holdings view
    this.accountsService.getPositions(accountId).subscribe({
      next: (res) => {
        this.basePositions.set(res);
      },
      error: (err) => {
        console.error('Failed to load holdings', err);
      }
    });
  }

  private resolveAccountId(accountIdFromToken?: unknown): number | null {
    const directId = Number(accountIdFromToken);
    if (Number.isInteger(directId) && directId > 0) {
      return directId;
    }

    const token = this.session.token();
    if (!token) {
      return null;
    }

    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const tokenAccountId = Number(payload.accountId);
      return Number.isInteger(tokenAccountId) && tokenAccountId > 0 ? tokenAccountId : null;
    } catch {
      return null;
    }
  }

  private resetPortfolioData() {
    this.baseBalance.set(0);
    this.basePositions.set([]);
    this.accountCurrency.set('USD');
  }
}

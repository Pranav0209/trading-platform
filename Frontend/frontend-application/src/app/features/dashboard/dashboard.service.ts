import { Injectable, inject, signal } from '@angular/core';
import { PortfolioSummary, Holding } from './models';
import { AccountsService } from '../../../generated/trade/api/accounts.service';
import { ProfileService } from '../../../generated/auth/api/profile.service';
import { Session } from '../../core/auth/session';


@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  private readonly accountsService = inject(AccountsService);
  private readonly profileService = inject(ProfileService);
  private readonly session = inject(Session);
  
  // Signals
  portfolioSummary = signal<PortfolioSummary>({
    portfolioValue: 0,
    todayPnL: 0,
    todayPnLPercent: 0,
    availableBalance: 0
  });
  holdings = signal<Holding[]>([]);
  userName = signal<string>('User');
  userEmail = signal<string | null>(null);
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
        this.portfolioSummary.set({
          portfolioValue: res.cashBalance,
          todayPnL: 0,
          todayPnLPercent: 0,
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
        const mappedPositions = res.map(p => ({
          symbol: p.symbol,
          quantity: p.quantity,
          averagePrice: p.averageCost,
          ltp: p.averageCost,
          pnl: 0
        }));
        this.holdings.set(mappedPositions);
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
    this.portfolioSummary.set({
      portfolioValue: 0,
      todayPnL: 0,
      todayPnLPercent: 0,
      availableBalance: 0
    });
    this.holdings.set([]);
    this.accountCurrency.set('USD');
  }
}

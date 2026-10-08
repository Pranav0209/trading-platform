import { Injectable, inject, signal, computed } from '@angular/core';
import { AccountsService } from '../../../generated/trade/api/accounts.service';
import { ProfileService } from '../../../generated/auth/api/profile.service';
import { PLService } from '../../../generated/portfolio/api/pL.service';
import { PortfolioResponse } from '../../../generated/trade/model/portfolioResponse';
import { RealisedPnlHistoryResponse } from '../../../generated/portfolio/model/realisedPnlHistoryResponse';
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
  private readonly plService = inject(PLService);

  readonly chartPath = signal<string>('');
  readonly chartFill = signal<string>('');
  readonly chartChange = signal<number>(0);
  readonly chartTimeRange = signal<string>('ALL');

  setChartTimeRange(range: string) {
    this.chartTimeRange.set(range);
    const accountId = this.resolveAccountId();
    if (accountId !== null) {
      this.loadChartData(accountId);
    }
  }
  
  // Base State
  private readonly basePositions = signal<PortfolioResponse[]>([]);
  private readonly baseBalance = signal<number>(0);
  
  readonly userName = signal<string>('User');
  readonly userEmail = signal<string | null>(null);
  readonly accountStatus = signal<string | null>(null);
  readonly accountCurrency = signal<string>('USD');

  // Computed Real-Time State
  readonly holdings = computed(() => {
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
        pnl: pnl,
        currentValue: ltp * p.quantity,
        investedValue: p.averageCost * p.quantity
      };
    });
  });

  readonly portfolioSummary = computed(() => {
    let currentHoldingsValue = 0;
    let investedValue = 0;
    let totalPnl = 0;
    
    this.holdings().forEach(h => {
       currentHoldingsValue += h.ltp * h.quantity;
       investedValue += h.investedValue;
       totalPnl += h.pnl;
    });

    const portfolioValue = this.baseBalance() + currentHoldingsValue;
    const pnlPercent = investedValue > 0 ? (totalPnl / investedValue) * 100 : 0;

    return {
      investedValue,
      currentHoldingsValue,
      portfolioValue,
      unrealizedPnL: totalPnl,
      unrealizedPnLPercent: Number(pnlPercent.toFixed(2)),
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
    this.accountsService.getPortfolio(accountId).subscribe({
      next: (res) => {
        this.basePositions.set(res);
      },
      error: (err) => {
        console.error('Failed to load holdings', err);
      }
    });

    // Fetch Historical PnL for Chart
    this.loadChartData(accountId);
  }

  private loadChartData(accountId: number) {
    let fromDate: string | undefined = undefined;
    const range = this.chartTimeRange();
    
    if (range !== 'ALL') {
      const date = new Date();
      if (range === '1D') date.setDate(date.getDate() - 1);
      else if (range === '1W') date.setDate(date.getDate() - 7);
      else if (range === '1M') date.setMonth(date.getMonth() - 1);
      else if (range === '6M') date.setMonth(date.getMonth() - 6);
      else if (range === '1Y') date.setFullYear(date.getFullYear() - 1);
      
      fromDate = date.toISOString().split('T')[0];
    }

    this.plService.getRealisedPnlHistory(accountId, undefined, fromDate, undefined, 0, 100).subscribe({
      next: (res) => {
        this.processChartData(res);
      },
      error: (err) => console.error('Failed to load PNL history', err)
    });
  }

  private processChartData(res: RealisedPnlHistoryResponse) {
    const records = res.records || [];
    const ascRecords = [...records].reverse();
    
    let cumulative = 0;
    const dataPoints = ascRecords.map(r => {
      cumulative += r.realisedPnl;
      return cumulative;
    });

    dataPoints.unshift(0); // starting baseline

    const min = Math.min(...dataPoints);
    const max = Math.max(...dataPoints);
    const range = max - min || 1;

    const width = 800;
    const height = 180; 
    const stepX = width / (dataPoints.length > 1 ? dataPoints.length - 1 : 1);

    let path = '';
    dataPoints.forEach((val, i) => {
      const x = i * stepX;
      const y = height - ((val - min) / range * height) + 10;
      path += (i === 0 ? 'M' : ' L') + `${x.toFixed(1)},${y.toFixed(1)}`;
    });

    if (dataPoints.length === 1) {
      path = `M0,100 L800,100`; // flat line if no data
    }

    this.chartPath.set(path);
    this.chartFill.set(`${path} L800,200 L0,200 Z`);
    this.chartChange.set(res.summary?.totalRealisedPnl || 0);
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

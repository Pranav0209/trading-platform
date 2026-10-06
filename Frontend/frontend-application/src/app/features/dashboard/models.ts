export interface PortfolioSummary {
  portfolioValue: number;
  todayPnL: number;
  todayPnLPercent: number;
  availableBalance: number;
}

export interface Holding {
  symbol: string;
  quantity: number;
  averagePrice: number;
  ltp: number;
  pnl: number;
}



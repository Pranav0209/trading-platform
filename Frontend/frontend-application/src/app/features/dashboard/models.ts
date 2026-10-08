export interface PortfolioSummary {
  investedValue: number;
  currentHoldingsValue: number;
  portfolioValue: number;
  unrealizedPnL: number;
  unrealizedPnLPercent: number;
  availableBalance: number;
}

export interface Holding {
  symbol: string;
  quantity: number;
  averagePrice: number;
  ltp: number;
  pnl: number;
}



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

export interface Position {
  symbol: string;
  quantity: number;
  averagePrice: number;
  ltp: number;
  pnl: number;
}

export interface Order {
  id: string;
  time: string;
  symbol: string;
  side: 'BUY' | 'SELL';
  quantity: number;
  price: number;
  status: 'FILLED' | 'PENDING' | 'REJECTED';
}

export interface SystemStatus {
  service: string;
  status: 'Operational' | 'Degraded' | 'Offline';
}

// Models for Watchlist and Price Alerts

export interface Watchlist {
  id: number;
  accountId: number;
  name: string;
  instrumentCount: number;
  createdAt: string;
}

export interface WatchlistDetails extends Watchlist {
  instruments: WatchlistInstrument[];
}

export interface WatchlistInstrument {
  symbol: string;
  displayName: string;
  currency: string;
  price: number | null;
  change: number | null;
  changePercent: number | null;
  asOf: string | null;
  addedAt: string;
}

export interface CreateWatchlistRequest {
  name: string;
}

export interface PriceAlert {
  id: number;
  accountId: number;
  symbol: string;
  displayName: string;
  direction: 'ABOVE' | 'BELOW';
  threshold: number;
  state: 'ACTIVE' | 'TRIGGERED' | 'DORMANT';
  lastFiredAt: string | null;
  deliveryStatus: 'PENDING' | 'DELIVERED' | 'FAILED';
  failureReason: string | null;
  createdAt: string;
}

export interface CreateAlertRequest {
  symbol: string;
  direction: 'ABOVE' | 'BELOW';
  threshold: number;
}

export interface AlertsResponse {
  alerts: PriceAlert[];
  count: number;
  limit: number;
}

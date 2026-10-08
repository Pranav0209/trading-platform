// Models for Watchlist and Price Alerts
// Re-export generated API types for consistency (using 'export type' for isolatedModules compatibility)
export type { AlertResponse as PriceAlert } from '../../../../generated/trade/model/models';
export type { AlertResponse } from '../../../../generated/trade/model/models';
export type { CreateAlertRequest } from '../../../../generated/trade/model/models';
export type { GetAlerts200Response as AlertsResponse } from '../../../../generated/trade/model/models';

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

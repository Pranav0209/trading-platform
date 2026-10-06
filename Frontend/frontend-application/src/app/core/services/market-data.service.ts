import { Injectable, signal, effect, inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { Session } from '../auth/session';

export interface QuotePayload {
  symbol: string;
  price: number;
  bid: number;
  ask: number;
  currency: string;
  change: number;
  changePercent: number;
  previousClose: number;
  asOf: string;
  marketState: string;
}

@Injectable({
  providedIn: 'root'
})
export class MarketDataService {
  private session = inject(Session);
  
  // Single source of truth for all real-time market data in the application
  quotes = signal<Record<string, QuotePayload>>({});
  
  private eventSource: EventSource | null = null;

  constructor() {
    // Load cached quotes from localStorage to prevent "Waiting..." on refresh
    try {
      const cached = localStorage.getItem('zkuba_quotes');
      if (cached) {
        this.quotes.set(JSON.parse(cached));
      }
    } catch (e) {
      console.error('Failed to load cached quotes', e);
    }

    effect(() => {
      const token = this.session.token();
      if (token) {
        this.connectToMarketData(token);
      } else if (this.eventSource) {
        this.eventSource.close();
        this.eventSource = null;
        this.quotes.set({}); // clear on logout
        localStorage.removeItem('zkuba_quotes');
      }
    });

    // Save quotes to localStorage whenever they update
    effect(() => {
      const currentQuotes = this.quotes();
      if (Object.keys(currentQuotes).length > 0) {
        localStorage.setItem('zkuba_quotes', JSON.stringify(currentQuotes));
      }
    });
  }

  private connectToMarketData(token: string) {
    if (this.eventSource) {
      this.eventSource.close();
    }
    
    // We are connecting to the stream endpoint provided by order-service
    const url = `${environment.tradeApiUrl}/api/v1/watchlists/stream?token=${token}`;
    this.eventSource = new EventSource(url);
    
    this.eventSource.addEventListener('quote', (event: any) => {
      try {
        const data = JSON.parse(event.data);
        if (data.symbol) {
          this.quotes.update(current => ({
            ...current,
            [data.symbol]: data
          }));
        }
      } catch (e) {
        console.error('Error parsing quote data', e);
      }
    });

    this.eventSource.onerror = (error) => {
      console.error('SSE Error:', error);
      // Optional: implement automatic reconnect logic with backoff
    };
  }
}

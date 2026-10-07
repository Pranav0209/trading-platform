import { Component, OnInit, OnDestroy, inject, signal, effect } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { WatchlistsService } from '../../../../generated/trade/api/watchlists.service';
import { InstrumentsService } from '../../../../generated/trade/api/instruments.service';
import { WatchlistResponse } from '../../../../generated/trade/model/watchlistResponse';
import { InstrumentResponse } from '../../../../generated/trade/model/instrumentResponse';
import { MarketDataService } from '../../../core/services/market-data.service';

@Component({
  selector: 'app-watchlist',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './watchlist.html',
  styleUrl: './watchlist.css'
})
export class WatchlistComponent implements OnInit {
  private readonly watchlistsService = inject(WatchlistsService);
  private readonly instrumentsService = inject(InstrumentsService);
  private readonly marketData = inject(MarketDataService);
  private readonly router = inject(Router);

  watchlists = signal<WatchlistResponse[]>([]);
  activeWatchlist = signal<WatchlistResponse | null>(null);
  
  availableInstruments = signal<InstrumentResponse[]>([]);
  filteredInstruments = signal<InstrumentResponse[]>([]);
  showDropdown = signal(false);
  
  // Expose the centralized quotes signal to the template
  quotes = this.marketData.quotes;
  
  newWatchlistName = '';
  newSymbol = '';

  ngOnInit() {
    this.loadWatchlists();
    this.instrumentsService.getInstruments().subscribe({
      next: (instruments) => {
        const tradable = instruments.filter(i => i.tradable);
        this.availableInstruments.set(tradable);
        this.filteredInstruments.set(tradable);
      },
      error: (e) => console.error('Failed to load instruments', e)
    });
  }

  loadWatchlists() {
    this.watchlistsService.getWatchlists().subscribe({
      next: (data) => {
        this.watchlists.set(data);
        if (data.length > 0 && !this.activeWatchlist()) {
          this.activeWatchlist.set(data[0]);
        } else if (this.activeWatchlist()) {
          const updated = data.find(w => w.id === this.activeWatchlist()?.id);
          this.activeWatchlist.set(updated || null);
        }
      },
      error: (e) => console.error('Failed to load watchlists', e)
    });
  }

  createWatchlist() {
    if (!this.newWatchlistName.trim()) return;
    this.watchlistsService.createWatchlist({ name: this.newWatchlistName }).subscribe({
      next: (data) => {
        this.newWatchlistName = '';
        this.activeWatchlist.set(data);
        this.loadWatchlists();
      },
      error: (e) => console.error('Failed to create watchlist', e)
    });
  }

  addSymbol() {
    const list = this.activeWatchlist();
    if (!list || !this.newSymbol.trim()) return;
    
    this.watchlistsService.addSymbolToWatchlist(list.id, this.newSymbol.toUpperCase()).subscribe({
      next: () => {
        this.newSymbol = '';
        this.loadWatchlists();
      },
      error: (e) => console.error('Failed to add symbol', e)
    });
  }

  removeSymbol(symbol: string) {
    const list = this.activeWatchlist();
    if (!list) return;
    
    this.watchlistsService.removeSymbolFromWatchlist(list.id, symbol).subscribe({
      next: () => {
        this.loadWatchlists();
      },
      error: (e) => console.error('Failed to remove symbol', e)
    });
  }

  selectedSymbol: string | null = null;
  isCreatingList = false;

  private previousPrices: Record<string, number> = {};
  private tickDirections: Record<string, 'up' | 'down'> = {};
  private flashStates: Record<string, 'price-up-flash' | 'price-down-flash' | null> = {};

  constructor() {
    effect(() => {
      const currentQuotes = this.quotes();
      for (const symbol in currentQuotes) {
        const newPrice = currentQuotes[symbol].price;
        const oldPrice = this.previousPrices[symbol];

        if (oldPrice !== undefined && newPrice !== oldPrice) {
          const isUp = newPrice > oldPrice;
          this.tickDirections[symbol] = isUp ? 'up' : 'down';
          const flashClass = isUp ? 'price-up-flash' : 'price-down-flash';
          this.flashStates[symbol] = flashClass;

          // Clear flash after 500ms
          setTimeout(() => {
            if (this.flashStates[symbol] === flashClass) {
              this.flashStates[symbol] = null;
            }
          }, 500);
        }
        this.previousPrices[symbol] = newPrice;
      }
    });
  }

  selectWatchlist(watchlist: WatchlistResponse) {
    this.activeWatchlist.set(watchlist);
    this.selectedSymbol = null;
  }

  selectInstrument(symbol: string) {
    this.selectedSymbol = symbol;
    this.router.navigate(['/trade'], { queryParams: { symbol: symbol } });
  }

  getInstrumentName(symbol: string): string {
    const inst = this.availableInstruments().find(i => i.symbol === symbol);
    return inst ? inst.displayName : 'Unknown Instrument';
  }

  getTickDirection(symbol: string, currentPrice: number): 'up' | 'down' | null {
    return this.tickDirections[symbol] || null;
  }

  getFlashClass(symbol: string, currentPrice: number): string | null {
    return this.flashStates[symbol] || null;
  }

  focusSearch() {
    setTimeout(() => {
      const input = document.querySelector('.search-input') as HTMLElement;
      if (input) input.focus();
    }, 10);
  }

  focusCreateInput() {
    setTimeout(() => {
      const input = document.querySelector('.tab-input') as HTMLElement;
      if (input) input.focus();
    }, 10);
  }

  abs(value: number): number {
    return Math.abs(value);
  }

  onSearchChange(value: string) {
    this.newSymbol = value;
    if (!value) {
      this.filteredInstruments.set(this.availableInstruments());
      return;
    }
    const lower = value.toLowerCase();
    this.filteredInstruments.set(
      this.availableInstruments().filter(i => 
        i.symbol.toLowerCase().includes(lower) || 
        i.displayName.toLowerCase().includes(lower)
      )
    );
  }

  selectSearchInstrument(symbol: string) {
    this.newSymbol = symbol;
    this.showDropdown.set(false);
    this.addSymbol();
  }

  hideDropdown() {
    setTimeout(() => {
      this.showDropdown.set(false);
    }, 200);
  }
}

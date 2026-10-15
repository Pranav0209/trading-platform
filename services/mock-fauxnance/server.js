const express = require('express');
const cors = require('cors');

const app = express();
app.use(cors());
app.use(express.json());

const PORT = process.env.PORT || 3001;

// Base instruments and baseline prices
const BASE_INSTRUMENTS = {
  'AAPL': { price: 313.53, currency: 'USD' },
  'MSFT': { price: 420.50, currency: 'USD' },
  'GOOGL': { price: 182.20, currency: 'USD' },
  'AMZN': { price: 186.75, currency: 'USD' },
  'META': { price: 505.30, currency: 'USD' },
  'TSLA': { price: 225.40, currency: 'USD' },
  'NVDA': { price: 128.60, currency: 'USD' },
  'NFLX': { price: 680.10, currency: 'USD' },
  'INTC': { price: 22.40, currency: 'USD' },
  'AMD': { price: 155.80, currency: 'USD' },
  'DIS': { price: 95.30, currency: 'USD' },
  'SBUX': { price: 94.20, currency: 'USD' },
  'NKE': { price: 82.50, currency: 'USD' },
  'ACME': { price: 100.00, currency: 'USD' },
  'INFY.NS': { price: 1910.00, currency: 'INR' },
  'RELIANCE.NS': { price: 2950.00, currency: 'INR' },
  'TCS.NS': { price: 4120.00, currency: 'INR' },
  'HDFCBANK.NS': { price: 1650.00, currency: 'INR' },
  'ICICIBANK.NS': { price: 1240.00, currency: 'INR' },
  'SBIN.NS': { price: 810.00, currency: 'INR' },
  'BHARTIARTL.NS': { price: 1680.00, currency: 'INR' },
  'ITC.NS': { price: 505.00, currency: 'INR' },
  'LT.NS': { price: 3620.00, currency: 'INR' },
  'HINDUNILVR.NS': { price: 2730.00, currency: 'INR' },
  'BAJFINANCE.NS': { price: 7150.00, currency: 'INR' },
  'FX:EURUSD': { price: 1.0850, currency: 'USD' },
  'X:BTC-USD': { price: 62500.00, currency: 'USD' }
};

function getInstrumentInfo(symbol) {
  const upper = (symbol || '').toUpperCase().trim();
  if (BASE_INSTRUMENTS[upper]) {
    return { symbol: upper, ...BASE_INSTRUMENTS[upper] };
  }
  // Generate deterministic base price for unknown symbols
  let hash = 0;
  for (let i = 0; i < upper.length; i++) {
    hash = (hash * 31 + upper.charCodeAt(i)) & 0xffff;
  }
  const price = Number((50 + (hash % 450) + (hash % 100) / 100).toFixed(2));
  const currency = upper.endsWith('.NS') ? 'INR' : 'USD';
  return { symbol: upper, price, currency };
}

function generateQuoteData(symbol) {
  const info = getInstrumentInfo(symbol);
  // Add small random price drift +/- 0.5%
  const drift = (Math.random() - 0.5) * 0.01;
  const price = Number((info.price * (1 + drift)).toFixed(4));
  const spreadBps = 2.0;
  const halfSpread = (price * (spreadBps / 10000)) / 2;
  const bid = Number((price - halfSpread).toFixed(4));
  const ask = Number((price + halfSpread).toFixed(4));
  const previousClose = Number((info.price * 0.995).toFixed(4));
  const change = Number((price - previousClose).toFixed(4));
  const changePercent = Number(((change / previousClose) * 100).toFixed(4));
  const asOf = new Date().toISOString();

  return {
    symbol: info.symbol,
    price,
    bid,
    ask,
    spreadBps,
    currency: info.currency,
    change,
    changePercent,
    previousClose,
    asOf,
    marketState: 'open'
  };
}

function generateCandles(symbol, count = 90) {
  const info = getInstrumentInfo(symbol);
  const candles = [];
  const now = new Date();
  let currentPrice = info.price * 0.92; // start 90 days ago slightly lower

  for (let i = count; i >= 0; i--) {
    const d = new Date(now.getTime() - i * 24 * 60 * 60 * 1000);
    const dateStr = d.toISOString().split('T')[0];

    const dailyDrift = (Math.random() - 0.49) * 0.03;
    const open = Number(currentPrice.toFixed(2));
    const close = Number((open * (1 + dailyDrift)).toFixed(2));
    const high = Number((Math.max(open, close) * (1 + Math.random() * 0.015)).toFixed(2));
    const low = Number((Math.min(open, close) * (1 - Math.random() * 0.015)).toFixed(2));
    const volume = Math.floor(1000000 + Math.random() * 5000000);

    candles.push({
      date: dateStr,
      open,
      high,
      low,
      close,
      adjclose: close,
      volume,
      synthetic: false
    });

    currentPrice = close;
  }

  return candles;
}

// Health check endpoints
app.get(['/health', '/v1/health'], (req, res) => {
  res.json({ status: 'ok', service: 'mock-fauxnance', timestamp: new Date().toISOString() });
});

// Batch quotes: GET /v1/quotes?symbols=AAPL,MSFT or GET /quotes?symbols=...
app.get(['/v1/quotes', '/quotes'], (req, res) => {
  const symbolsParam = req.query.symbols;
  if (!symbolsParam) {
    return res.status(400).json({ error: 'Missing symbols query parameter' });
  }

  const symbols = symbolsParam.split(',').map(s => s.trim()).filter(Boolean);
  const quotes = symbols.map(sym => {
    const quote = generateQuoteData(sym);
    return {
      symbol: quote.symbol,
      source: 'mock-cache',
      stale: false,
      quote
    };
  });

  res.json({
    data: { quotes },
    meta: {
      asOf: new Date().toISOString(),
      disclaimer: 'Internal mock market data for testing.',
      spreadSource: 'modelled'
    }
  });
});

// Single quote: GET /v1/quotes/:symbol or GET /quotes/:symbol
app.get(['/v1/quotes/:symbol', '/quotes/:symbol'], (req, res) => {
  const symbol = req.params.symbol;
  const quoteData = generateQuoteData(symbol);

  res.json({
    data: quoteData,
    meta: {
      asOf: new Date().toISOString(),
      disclaimer: 'Internal mock market data for testing.',
      symbol: quoteData.symbol,
      source: 'mock-stored',
      stale: false,
      partial: false,
      availableFrom: '2015-01-02',
      spreadSource: 'modelled'
    }
  });
});

// Historical candles: GET /v1/candles/:symbol or GET /candles/:symbol
app.get(['/v1/candles/:symbol', '/candles/:symbol'], (req, res) => {
  const symbol = req.params.symbol;
  const info = getInstrumentInfo(symbol);
  const candles = generateCandles(symbol, 90);

  res.json({
    data: {
      symbol: info.symbol,
      currency: info.currency,
      interval: '1d',
      candles
    },
    meta: {
      symbol: info.symbol,
      source: 'mock-stored',
      lastRefreshed: Math.floor(Date.now() / 1000)
    }
  });
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Mock Fauxnance API server running on port ${PORT}`);
});

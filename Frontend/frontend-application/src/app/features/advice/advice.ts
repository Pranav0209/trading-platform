import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AdviceService } from '../../../generated/trade/api/advice.service';
import { AdviceResponse, SignalResponse } from '../../../generated/trade/model/models';
import { Session } from '../../core/auth/session';

@Component({
  selector: 'app-advice',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './advice.html',
  styleUrl: './advice.css',
})
export class Advice implements OnInit {
  private adviceService = inject(AdviceService);
  private session = inject(Session);

  loading = signal(true);
  error = signal<string | null>(null);
  advice = signal<AdviceResponse | null>(null);

  ngOnInit() {
    this.loadAdvice();
  }

  loadAdvice() {
    this.loading.set(true);
    this.error.set(null);
    
    const accountId = this.getAccountId();
    if (!accountId) {
      this.error.set('No active account found. Please ensure you are logged in and have an active trading account.');
      this.loading.set(false);
      return;
    }

    this.adviceService.getAdvice(accountId).subscribe({
      next: (res) => {
        this.advice.set(res);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load trade advice', err);
        this.error.set('Failed to generate trade advice. Please try again later.');
        this.loading.set(false);
      }
    });
  }

  private getAccountId(): number | null {
    const token = this.session.token();
    if (!token) return null;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const accountId = Number(payload.accountId);
      return Number.isInteger(accountId) && accountId > 0 ? accountId : null;
    } catch {
      return null;
    }
  }

  getScoreClass(score: number): string {
    if (score >= 3) return 'score-bullish';
    if (score <= -3) return 'score-bearish';
    return 'score-neutral';
  }

  getSignalBadgeClass(signal: string): string {
    if (signal === 'BUY') return 'badge-buy';
    if (signal === 'SELL') return 'badge-sell';
    if (signal === 'HOLD') return 'badge-hold';
    return 'badge-insufficient';
  }
}

import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { AccountsService } from '../../../../generated/trade/api/accounts.service';
import { Session } from '../../auth/session';
import { Position } from '../../dashboard/models';

@Component({
  selector: 'app-positions',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './positions.html',
  styleUrls: ['./positions.css']
})
export class PositionsComponent implements OnInit {
  private accountsService = inject(AccountsService);
  private session = inject(Session);
  private router = inject(Router);
  
  positions = signal<Position[]>([]);
  isStandalone = signal<boolean>(false);

  ngOnInit() {
    this.isStandalone.set(this.router.url === '/positions');
    const token = this.session.token();
    if (!token) return;

    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const accountId = payload.accountId || payload.userId;
      if (accountId) {
        this.accountsService.getPositions(accountId).subscribe({
          next: (res) => {
            const mappedPositions = res.map(p => ({
              symbol: p.symbol,
              quantity: p.quantity,
              averagePrice: p.averageCost,
              ltp: p.averageCost, // Mock LTP as averageCost for now
              pnl: 0 // Mock P&L for now
            }));
            this.positions.set(mappedPositions);
          },
          error: (err) => console.error('Failed to load positions', err)
        });
      }
    } catch (e) {
      console.error('Failed to parse token for positions', e);
    }
  }
}

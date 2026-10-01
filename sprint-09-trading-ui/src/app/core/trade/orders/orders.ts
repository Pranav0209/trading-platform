import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { AccountsService } from '../../../../generated/trade/api/accounts.service';
import { Session } from '../../auth/session';
import { Order } from '../../dashboard/models';

@Component({
  selector: 'app-orders',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './orders.html',
  styleUrls: ['./orders.css']
})
export class OrdersComponent implements OnInit {
  private accountsService = inject(AccountsService);
  private session = inject(Session);
  private router = inject(Router);
  
  recentOrders = signal<Order[]>([]);
  isStandalone = signal<boolean>(false);

  ngOnInit() {
    this.isStandalone.set(this.router.url === '/orders');
    const token = this.session.token();
    if (!token) return;

    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const accountId = payload.accountId || payload.userId;
      if (accountId) {
        this.accountsService.getOrders(accountId).subscribe({
          next: (res) => {
            const mappedOrders = res.map(o => ({
              id: o.orderId,
              time: new Date(o.createdOn).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
              symbol: o.symbol,
              side: o.side as 'BUY' | 'SELL',
              quantity: o.quantity,
              price: o.price,
              executedPrice: o.executedPrice,
              status: o.status as 'FILLED' | 'PENDING' | 'REJECTED' | 'NEW'
            }));
            this.recentOrders.set(mappedOrders);
          },
          error: (err) => console.error('Failed to load orders', err)
        });
      }
    } catch (e) {
      console.error('Failed to parse token for orders', e);
    }
  }
}

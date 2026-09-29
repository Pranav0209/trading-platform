import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AccountsService } from '../../../generated/trade/api/accounts.service';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './admin-dashboard.html',
  styleUrls: ['./admin-dashboard.css']
})
export class AdminDashboard implements OnInit {
  private accountsService = inject(AccountsService);
  
  pendingUsers = signal<any[]>([]);

  ngOnInit() {
    this.loadPendingUsers();
  }

  loadPendingUsers() {
    this.accountsService.getPendingUsers().subscribe({
      next: (users) => this.pendingUsers.set(users),
      error: (err) => console.error('Failed to load pending users', err)
    });
  }

  createAccount(user: any, currency: string, balanceStr: string) {
    const numericUserId = parseInt(user.id, 10);
    const initialBalance = parseFloat(balanceStr);
    
    if (isNaN(numericUserId)) {
      alert('Error: user ID is not numeric.');
      return;
    }
    
    if (isNaN(initialBalance) || initialBalance <= 0) {
      alert('Error: Invalid initial balance. It must be greater than 0.');
      return;
    }

    this.accountsService.createAccount({
      userId: numericUserId,
      currency: currency || 'USD',
      initialBalance: initialBalance
    }).subscribe({
      next: (res: any) => {
        const updated = this.pendingUsers().filter(u => u.id !== user.id);
        this.pendingUsers.set(updated);
        alert('Account created successfully! Account ID: ' + res.accountId);
      },
      error: (err: any) => {
        console.error('Failed to create account', err);
        alert('Failed to create account.');
      }
    });
  }
}

import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { AccountsService } from '../../../generated/trade/api/accounts.service';
import { ErrorCodeMapper } from '../../core/error/error-code-mapper';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './admin-dashboard.html',
  styleUrls: ['./admin-dashboard.css']
})
export class AdminDashboard implements OnInit {
  private accountsService = inject(AccountsService);
  private errorCodeMapper = inject(ErrorCodeMapper);
  
  pendingUsers = signal<any[]>([]);
  isLoading = signal(false);
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);
  creatingAccountUserId = signal<string | null>(null);

  ngOnInit() {
    this.loadPendingUsers();
  }

  loadPendingUsers() {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    
    this.accountsService.getPendingUsers().subscribe({
      next: (users) => {
        this.pendingUsers.set(users);
        this.isLoading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.errorMessage.set(this.errorCodeMapper.mapErrorToMessage(err));
        this.isLoading.set(false);
        console.error('Failed to load pending users', err);
      }
    });
  }

  createAccount(user: any, currency: string, balanceStr: string) {
    // Validate input
    const numericUserId = parseInt(user.id, 10);
    const initialBalance = parseFloat(balanceStr);
    
    if (isNaN(numericUserId)) {
      this.errorMessage.set('User ID is not numeric.');
      return;
    }
    
    if (isNaN(initialBalance) || initialBalance <= 0) {
      this.errorMessage.set('Invalid initial balance. It must be greater than 0.');
      return;
    }

    this.creatingAccountUserId.set(user.id);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    this.accountsService.createAccount({
      userId: numericUserId,
      currency: currency || 'USD',
      initialBalance: initialBalance
    }).subscribe({
      next: (res: any) => {
        this.creatingAccountUserId.set(null);
        this.successMessage.set(res.message || 'Account created successfully');
        
        // Remove user from pending list
        const updated = this.pendingUsers().filter(u => u.id !== user.id);
        this.pendingUsers.set(updated);
        
        // Clear success message after 5 seconds
        setTimeout(() => {
          if (this.successMessage() === (res.message || `Account created successfully! Account ID: ${res.accountId}`)) {
            this.successMessage.set(null);
          }
        }, 5000);
      },
      error: (err: HttpErrorResponse) => {
        this.creatingAccountUserId.set(null);
        this.errorMessage.set(this.errorCodeMapper.mapErrorToMessage(err));
        console.error('Failed to create account', err);
      }
    });
  }

  clearMessages() {
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }
}

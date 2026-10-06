import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Session } from '../../core/auth/session';
import { AccountsService } from '../../../generated/trade/api/accounts.service';
import { HttpErrorResponse } from '@angular/common/http';
import { ErrorCodeMapper } from '../../core/error/error-code-mapper';

/**
 * Trading Account Page - displays account details and balance
 * Fetches account information from the Trade API
 */
@Component({
  selector: 'app-account',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './account.html',
  styleUrl: './account.css',
})
export class Account implements OnInit {
  private readonly session = inject(Session);
  private readonly accountsService = inject(AccountsService);
  private readonly errorCodeMapper = inject(ErrorCodeMapper);

  // Account data
  accountNumber = signal<string>('');
  cashBalance = signal<number>(0);
  currency = signal<string>('USD');
  status = signal<string>('');
  isLoading = signal(true);
  error = signal<string | null>(null);

  // Masking state
  isAccountNumberRevealed = signal(false);
  isBalanceRevealed = signal(false);

  ngOnInit(): void {
    this.loadAccountDetails();
  }

  /**
   * Toggle account number visibility
   */
  toggleAccountNumberVisibility(): void {
    this.isAccountNumberRevealed.update(v => !v);
  }

  /**
   * Toggle balance visibility
   */
  toggleBalanceVisibility(): void {
    this.isBalanceRevealed.update(v => !v);
  }

  /**
   * Get masked account number (shows only last 4 digits)
   */
  getMaskedAccountNumber(): string {
    const acc = this.accountNumber();
    if (acc.length <= 4) return '****';
    return '****' + acc.slice(-4);
  }

  /**
   * Get masked balance (shows only first and last digit)
   */
  getMaskedBalance(): string {
    const balance = this.cashBalance();
    return `${'*'.repeat(Math.max(1, Math.floor(balance).toString().length - 1))}${balance.toString().slice(-1)}`;
  }

  /**
   * Format currency for display
   */
  formatCurrency(amount: number): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: this.currency(),
    }).format(amount);
  }

  /**
   * Load account details from API
   */
  private loadAccountDetails(): void {
    // Get accountId from JWT token
    const token = this.session.token();
    if (!token) {
      this.error.set('No active session');
      this.isLoading.set(false);
      return;
    }

    let accountId: number | null = null;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      accountId = payload.accountId;
    } catch (e) {
      this.error.set('Failed to extract account information');
      this.isLoading.set(false);
      return;
    }

    if (!accountId) {
      this.error.set('No trading account associated with this user');
      this.isLoading.set(false);
      return;
    }

    // Fetch account details
    this.accountsService.getAccount(accountId).subscribe({
      next: (account) => {
        this.accountNumber.set(account['accountId'] || `ACC-${accountId}`);
        this.cashBalance.set(account['cashBalance'] || 0);
        this.currency.set(account['currency'] || 'USD');
        this.status.set(account['status'] || 'ACTIVE');
        this.isLoading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.error.set(this.errorCodeMapper.mapErrorToMessage(error));
        this.isLoading.set(false);
      },
    });
  }
}

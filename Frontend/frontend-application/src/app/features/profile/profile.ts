import { Component, inject, signal, computed, NgZone } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Session } from '../../core/auth/session';
import { AccountsService } from '../../../generated/trade/api/accounts.service';
import { HttpErrorResponse } from '@angular/common/http';
import { ErrorCodeMapper } from '../../core/error/error-code-mapper';

/**
 * User Profile Page - displays personal information
 * Username and role extracted from JWT token
 * Account number fetched from API
 */
@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './profile.html',
  styleUrl: './profile.css',
})
export class Profile {
  private readonly session = inject(Session);
  private readonly accountsService = inject(AccountsService);
  private readonly errorCodeMapper = inject(ErrorCodeMapper);
  private readonly ngZone = inject(NgZone);

  // Signals for JWT payload data
  username = signal<string>('');
  email = signal<string>('');
  roles = signal<string[]>([]);
  accountId = signal<number | null>(null);
  
  // Signals for API data
  accountNumber = signal<string>('');
  isLoading = signal<boolean>(true);
  error = signal<string>('');

  // Computed values
  userRole = computed(() => this.roles()[0] || 'User');
  roleDisplay = computed(() => {
    const role = this.userRole();
    return role === 'ADMIN' ? 'Administrator' : 'Trader';
  });
  displayEmail = computed(() => {
    const email = this.email();
    if (email) return email;
    const user = this.username();
    return user ? `${user}@example.com` : 'Not available';
  });

  constructor() {
    this.initializeFromToken();
  }

  /**
   * Extract user information from JWT token
   */
  private initializeFromToken(): void {
    const token = this.session.token();
    if (!token) return;

    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      this.username.set(payload.sub || 'User');
      this.email.set(payload.email || '');
      this.roles.set(payload.roles || []);
      const accountId = payload.accountId || null;
      this.accountId.set(accountId);
      
      if (accountId) {
        this.loadAccountNumber(accountId);
      }
    } catch (error) {
      console.error('Failed to parse JWT token:', error);
      this.isLoading.set(false);
    }
  }

  /**
   * Fetch account number from API
   */
  private loadAccountNumber(accountId: number): void {
    this.ngZone.run(() => {
      this.accountsService.getAccount(accountId).subscribe({
        next: (account) => {
          this.accountNumber.set(account['accountId'] || `ACC-${accountId}`);
          this.isLoading.set(false);
        },
        error: (error: HttpErrorResponse) => {
          console.error('Failed to load account number:', error);
          this.accountNumber.set(`ACC-${accountId}`);
          this.isLoading.set(false);
        },
      });
    });
  }

  /**
   * Get current date formatted
   */
  getCurrentDate(): string {
    return new Date().toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }
}

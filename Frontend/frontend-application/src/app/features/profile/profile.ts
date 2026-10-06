import { Component, inject, signal, computed, NgZone } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Session } from '../../core/auth/session';
import { AccountsService } from '../../../generated/trade/api/accounts.service';
import { HttpErrorResponse } from '@angular/common/http';
import { ErrorCodeMapper } from '../../core/error/error-code-mapper';
import { ProfileService } from '../../../generated/auth/api/profile.service';
import { ToastService } from '../../core/notifications/toast.service';
import { FormsModule } from '@angular/forms';

/**
 * User Profile Page - displays personal information
 * Username and role extracted from JWT token
 * Account number fetched from API
 */
@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './profile.html',
  styleUrl: './profile.css',
})
export class Profile {
  private readonly session = inject(Session);
  private readonly accountsService = inject(AccountsService);
  private readonly errorCodeMapper = inject(ErrorCodeMapper);
  private readonly ngZone = inject(NgZone);
  private readonly profileService = inject(ProfileService);
  private readonly toastService = inject(ToastService);

  // Signals for JWT payload data
  username = signal<string>('');
  email = signal<string>('');
  roles = signal<string[]>([]);
  accountId = signal<number | null>(null);
  
  // Signals for API data
  accountNumber = signal<string>('');
  isLoading = signal<boolean>(true);
  error = signal<string>('');
  
  // Preferences data
  notificationChannel = signal<string>('EMAIL');
  isUpdatingPreference = signal<boolean>(false);

  // Email update state
  newEmail = signal<string>('');
  emailUpdateStep = signal<'input' | 'otp'>('input');
  otpCode = signal<string>('');
  isUpdatingEmail = signal<boolean>(false);
  isEditingEmail = signal<boolean>(false);

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
    this.loadPreferences();
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

  /**
   * Fetch user preferences
   */
  private loadPreferences(): void {
    this.profileService.getPreferences()
      .subscribe({
        next: (prefs) => {
          this.notificationChannel.set(prefs.notificationChannel);
        },
        error: (err) => {
          console.error('Failed to load preferences:', err);
        }
      });
  }

  /**
   * Update notification channel preference
   */
  updatePreference(channel: string): void {
    if (this.notificationChannel() === channel) return;
    
    this.isUpdatingPreference.set(true);
    // TypeScript might expect an object matching UpdatePreferenceRequest or similar
    this.profileService.updatePreferences({ notificationChannel: channel as any })
      .subscribe({
        next: (prefs) => {
          this.notificationChannel.set(prefs.notificationChannel);
          this.isUpdatingPreference.set(false);
          this.toastService.success(`Notification preference updated to ${channel === 'EMAIL' ? 'Email' : 'Push'}`);
        },
        error: (err) => {
          console.error('Failed to update preference:', err);
          this.isUpdatingPreference.set(false);
          this.toastService.error('Failed to update notification preference');
        }
      });
  }

  /**
   * Request email update
   */
  requestEmailUpdate(): void {
    if (!this.newEmail() || !this.newEmail().includes('@')) {
      this.toastService.error('Please enter a valid email address');
      return;
    }

    this.isUpdatingEmail.set(true);
    this.profileService.requestEmailUpdate({ newEmail: this.newEmail() })
      .subscribe({
        next: () => {
          this.toastService.success(`OTP sent to ${this.newEmail()}`);
          this.emailUpdateStep.set('otp');
          this.isUpdatingEmail.set(false);
        },
        error: (err) => {
          console.error('Failed to request email update:', err);
          this.toastService.error(err.error?.message || 'Failed to request email update');
          this.isUpdatingEmail.set(false);
        }
      });
  }

  /**
   * Verify OTP and complete email update
   */
  verifyEmailUpdate(): void {
    if (!this.otpCode() || this.otpCode().length < 6) {
      this.toastService.error('Please enter a valid OTP');
      return;
    }

    this.isUpdatingEmail.set(true);
    this.profileService.verifyEmailUpdate({ 
      newEmail: this.newEmail(),
      otpCode: this.otpCode() 
    }).subscribe({
        next: () => {
          this.toastService.success('Email updated successfully!');
          this.email.set(this.newEmail());
          
          // Reset state
          this.newEmail.set('');
          this.otpCode.set('');
          this.emailUpdateStep.set('input');
          this.isUpdatingEmail.set(false);
          this.isEditingEmail.set(false);
        },
        error: (err) => {
          console.error('Failed to verify OTP:', err);
          this.toastService.error(err.error?.message || 'Invalid or expired OTP');
          this.isUpdatingEmail.set(false);
        }
      });
  }

  cancelEmailUpdate(): void {
    this.newEmail.set('');
    this.otpCode.set('');
    this.emailUpdateStep.set('input');
    this.isEditingEmail.set(false);
  }
}

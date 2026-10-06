import { Injectable, signal, computed, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class Session {
  private tokenSignal = signal<string | null>(localStorage.getItem('token'));
  private httpClient = inject(HttpClient);
  private router = inject(Router);

  constructor() {
    window.addEventListener('storage', (event) => {
      if (event.key === 'token') {
        this.tokenSignal.set(event.newValue);
        if (!event.newValue) {
          // If token was removed in another tab, redirect this tab to sign-in
          this.router.navigate(['/sign-in']);
        }
      }
    });
  }

  get token() {
    return this.tokenSignal.asReadonly();
  }

  get isLoggedIn() {
    return this.tokenSignal() !== null;
  }

  isAdmin = computed(() => {
    const token = this.tokenSignal();
    if (!token) return false;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.roles?.includes('ADMIN') === true;
    } catch {
      return false;
    }
  });

  userName = computed(() => {
    const token = this.tokenSignal();
    if (!token) return 'User';
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.sub || 'User';
    } catch {
      return 'User';
    }
  });

  setToken(token: string) {
    localStorage.setItem('token', token);
    this.tokenSignal.set(token);
  }

  clearToken() {
    localStorage.removeItem('token');
    this.tokenSignal.set(null);
  }

  async performLogout() {
    try {
      await this.httpClient.post(`${environment.authApiUrl}/auth/logout`, {}, { withCredentials: true }).toPromise();
    } catch (e) {
      console.error('Backend logout failed', e);
    }
    this.clearToken();
    this.router.navigate(['/sign-in']);
  }
}

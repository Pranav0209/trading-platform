import { Injectable, signal, computed } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class Session {
  private tokenSignal = signal<string | null>(localStorage.getItem('token'));

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
}

import { Injectable, signal } from '@angular/core';

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

  setToken(token: string) {
    localStorage.setItem('token', token);
    this.tokenSignal.set(token);
  }

  clearToken() {
    localStorage.removeItem('token');
    this.tokenSignal.set(null);
  }
}

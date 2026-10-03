import { Injectable, signal, effect } from '@angular/core';

type Theme = 'light' | 'dark';

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  private readonly themeSignal = signal<Theme>(this.getInitialTheme());
  readonly theme = this.themeSignal.asReadonly();

  constructor() {
    // Update DOM whenever theme changes
    effect(() => {
      const currentTheme = this.theme();
      const root = document.documentElement;
      
      if (currentTheme === 'dark') {
        root.classList.add('dark-theme');
        root.classList.remove('light-theme');
      } else {
        root.classList.add('light-theme');
        root.classList.remove('dark-theme');
      }
      
      // Persist preference
      localStorage.setItem('theme-preference', currentTheme);
    });

    // Listen to system preference changes
    if (window.matchMedia) {
      window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
        const stored = localStorage.getItem('theme-preference');
        if (!stored) {
          this.themeSignal.set(e.matches ? 'dark' : 'light');
        }
      });
    }
  }

  private getInitialTheme(): Theme {
    // 1. Check localStorage
    const stored = localStorage.getItem('theme-preference');
    if (stored === 'light' || stored === 'dark') {
      return stored;
    }

    // 2. Check system preference
    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
      return 'dark';
    }

    // 3. Default to light
    return 'light';
  }

  toggleTheme(): void {
    this.themeSignal.update(current => current === 'light' ? 'dark' : 'light');
  }

  setTheme(theme: Theme): void {
    this.themeSignal.set(theme);
  }

  getCurrentTheme(): Theme {
    return this.themeSignal();
  }
}

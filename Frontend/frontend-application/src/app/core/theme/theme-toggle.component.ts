import { Component, ViewEncapsulation, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ThemeService } from './theme.service';

@Component({
  selector: 'app-theme-toggle',
  standalone: true,
  imports: [CommonModule],
  encapsulation: ViewEncapsulation.None,
  template: `
    <div class="theme-toggle">
      <button 
        (click)="toggleTheme()" 
        class="toggle-button"
        [title]="'Switch to ' + (theme() === 'light' ? 'dark' : 'light') + ' mode'"
        type="button"
      >
        <span class="toggle-track">
          <span class="toggle-dot"></span>
        </span>
        <span class="toggle-label">
          {{ theme() === 'light' ? 'Light' : 'Dark' }}
        </span>
      </button>
    </div>
  `,
  styles: [`
    .theme-toggle {
      display: flex;
      align-items: center;
    }

    .toggle-button {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 6px 12px;
      border: none;
      border-radius: 4px;
      background-color: transparent;
      cursor: pointer;
      font-size: 12px;
      font-weight: 500;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      transition: all 200ms ease;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', sans-serif;
    }

    html:not(.dark-theme) .toggle-button {
      color: #F5F5DC;
      background-color: rgba(245, 245, 220, 0.16);
    }

    html:not(.dark-theme) .toggle-button:hover {
      background-color: rgba(245, 245, 220, 0.24);
    }

    html.dark-theme .toggle-button {
      color: #00D084;
      background-color: rgba(0, 208, 132, 0.14);
    }

    html.dark-theme .toggle-button:hover {
      background-color: rgba(0, 208, 132, 0.22);
    }

    .toggle-track {
      display: inline-flex;
      align-items: center;
      justify-content: flex-start;
      width: 28px;
      height: 16px;
      border-radius: 8px;
      transition: background-color 200ms ease;
      position: relative;
    }

    html:not(.dark-theme) .toggle-track {
      background-color: rgba(245, 245, 220, 0.35);
    }

    html.dark-theme .toggle-track {
      background-color: rgba(0, 208, 132, 0.32);
    }

    .toggle-dot {
      display: inline-block;
      width: 12px;
      height: 12px;
      border-radius: 50%;
      transition: transform 200ms ease;
      margin: 2px 2px;
    }

    html:not(.dark-theme) .toggle-dot {
      background-color: #F5F5DC;
    }

    html.dark-theme .toggle-dot {
      background-color: #00D084;
      transform: translateX(12px);
    }

    .toggle-label {
      min-width: 32px;
      text-align: center;
    }
  `]
})
export class ThemeToggleComponent {
  protected readonly themeService = inject(ThemeService);
  readonly theme = this.themeService.theme;

  toggleTheme(): void {
    this.themeService.toggleTheme();
  }
}

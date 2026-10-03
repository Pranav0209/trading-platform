import { Component, OnInit, ViewEncapsulation, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ThemeService } from './core/theme/theme.service';
import { ThemeToggleComponent } from './core/theme/theme-toggle.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ThemeToggleComponent],
  templateUrl: './app.html',
  styleUrl: './app.css',
  encapsulation: ViewEncapsulation.None
})
export class App implements OnInit {
  private readonly themeService = inject(ThemeService);

  ngOnInit() {
    // Initialize theme on app startup
    const initialTheme = this.themeService.getCurrentTheme();
    const root = document.documentElement;
    
    if (initialTheme === 'dark') {
      root.classList.add('dark-theme');
      root.classList.remove('light-theme');
    } else {
      root.classList.add('light-theme');
      root.classList.remove('dark-theme');
    }
  }

}

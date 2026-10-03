import { Component, ViewEncapsulation, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { ThemeToggleComponent } from '../theme/theme-toggle.component';
import { DashboardService } from '../../features/dashboard/dashboard.service';
import { Session } from '../auth/session';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, ThemeToggleComponent],
  templateUrl: './layout.html',
  styleUrls: ['./layout.css'],
  encapsulation: ViewEncapsulation.None
})
export class LayoutComponent {
  private dashboardService = inject(DashboardService);
  private router = inject(Router);
  private session = inject(Session);

  userName = this.dashboardService.userName;
  userInitial = computed(() => this.userName().charAt(0));
  isDropdownOpen = signal(false);

  toggleDropdown() {
    this.isDropdownOpen.set(!this.isDropdownOpen());
  }

  logout() {
    this.session.clearToken();
    this.router.navigate(['/sign-in']);
  }
}

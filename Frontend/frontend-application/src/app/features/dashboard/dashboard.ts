import { Component, ViewEncapsulation, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { DashboardService } from './dashboard.service';
import { Session } from '../../core/auth/session';
import { PositionsComponent } from '../trade/positions/positions';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, PositionsComponent],
  templateUrl: './dashboard.html',
  styleUrls: ['./dashboard.css'],
  encapsulation: ViewEncapsulation.None
})
export class Dashboard implements OnInit {
  private dashboardService = inject(DashboardService);
  private router = inject(Router);
  private session = inject(Session);

  // Expose signals to the template
  portfolioSummary = this.dashboardService.portfolioSummary;
  holdings = this.dashboardService.holdings;
  accountStatus = this.dashboardService.accountStatus;
  accountCurrency = this.dashboardService.accountCurrency;

  userName = this.session.userName;
  userInitial = computed(() => this.userName().charAt(0));
  
  isDropdownOpen = signal(false);

  currentTime = 'Good morning';

  ngOnInit() {
    const hour = new Date().getHours();
    if (hour >= 12 && hour < 17) {
      this.currentTime = 'Good afternoon';
    } else if (hour >= 17) {
      this.currentTime = 'Good evening';
    }
  }

  toggleDropdown() {
    this.isDropdownOpen.set(!this.isDropdownOpen());
  }

  logout() {
    this.session.performLogout();
  }
}

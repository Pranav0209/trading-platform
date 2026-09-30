import { Component, inject, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { DashboardService } from './dashboard.service';
import { Session } from '../auth/session';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard.html',
  styleUrls: ['./dashboard.css']
})
export class Dashboard implements OnInit {
  private dashboardService = inject(DashboardService);
  private router = inject(Router);
  private session = inject(Session);

  // Expose signals to the template
  portfolioSummary = this.dashboardService.portfolioSummary;
  holdings = this.dashboardService.holdings;
  positions = this.dashboardService.positions;
  recentOrders = this.dashboardService.recentOrders;
  systemStatuses = this.dashboardService.systemStatuses;
  userName = this.dashboardService.userName;
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
    this.session.clearToken();
    this.router.navigate(['/sign-in']);
  }
}

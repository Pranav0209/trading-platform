import { Component, ViewEncapsulation, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { DashboardService } from './dashboard.service';
import { Session } from '../../core/auth/session';
import { PortfolioComponent } from '../trade/portfolio/portfolio';
import { RealisedPnlComponent } from '../trade/realised-pnl/realised-pnl';

type HoldingSlice = {
  label: string;
  value: number;
  percent: number;
  color: string;
  path: string;
};

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, PortfolioComponent, RealisedPnlComponent],
  templateUrl: './dashboard.html',
  styleUrls: ['./dashboard.css'],
  encapsulation: ViewEncapsulation.None
})
export class Dashboard implements OnInit {
  private readonly dashboardService = inject(DashboardService);
  private readonly router = inject(Router);
  private readonly session = inject(Session);
  private readonly pieColors = ['#006747', '#1fb278', '#f59e0b', '#ef4444', '#0ea5e9', '#8b5cf6'];

  // Expose signals to the template
  portfolioSummary = this.dashboardService.portfolioSummary;
  holdings = this.dashboardService.holdings;
  accountStatus = this.dashboardService.accountStatus;
  accountCurrency = this.dashboardService.accountCurrency;
  accountId = this.session.accountId;
  chartPath = this.dashboardService.chartPath;
  chartFill = this.dashboardService.chartFill;
  chartChange = this.dashboardService.chartChange;
  chartTimeRange = this.dashboardService.chartTimeRange;
  holdingsTotal = computed(() => this.holdings().reduce((sum, holding) => sum + holding.currentValue, 0));
  holdingSlices = computed<HoldingSlice[]>(() => {
    const positions = [...this.holdings()]
      .filter(position => position.currentValue > 0)
      .sort((left, right) => right.currentValue - left.currentValue);

    const total = positions.reduce((sum, position) => sum + position.currentValue, 0);
    if (total <= 0) {
      return [];
    }

    const topPositions = positions.slice(0, 5).map(position => ({
      label: position.symbol,
      value: position.currentValue
    }));
    const remainingValue = positions.slice(5).reduce((sum, position) => sum + position.currentValue, 0);

    if (remainingValue > 0) {
      topPositions.push({ label: 'Others', value: remainingValue });
    }

    let startAngle = -90;
    return topPositions.map((position, index) => {
      const percent = position.value / total;
      const sweepAngle = percent * 360;
      const endAngle = startAngle + sweepAngle;
      const path = this.describePieSlice(60, 60, 52, startAngle, endAngle);
      startAngle = endAngle;

      return {
        label: position.label,
        value: position.value,
        percent,
        color: this.pieColors[index % this.pieColors.length],
        path
      };
    });
  });

  setChartTimeRange(range: string) {
    this.dashboardService.setChartTimeRange(range);
  }

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
    void this.session.performLogout();
  }

  private describePieSlice(
    centerX: number,
    centerY: number,
    radius: number,
    startAngle: number,
    endAngle: number
  ) {
    const start = this.polarToCartesian(centerX, centerY, radius, endAngle);
    const end = this.polarToCartesian(centerX, centerY, radius, startAngle);
    const largeArcFlag = endAngle - startAngle > 180 ? 1 : 0;

    return [
      `M ${centerX} ${centerY}`,
      `L ${start.x} ${start.y}`,
      `A ${radius} ${radius} 0 ${largeArcFlag} 0 ${end.x} ${end.y}`,
      'Z'
    ].join(' ');
  }

  private polarToCartesian(centerX: number, centerY: number, radius: number, angleInDegrees: number) {
    const angleInRadians = (angleInDegrees * Math.PI) / 180;
    return {
      x: centerX + (radius * Math.cos(angleInRadians)),
      y: centerY + (radius * Math.sin(angleInRadians))
    };
  }
}

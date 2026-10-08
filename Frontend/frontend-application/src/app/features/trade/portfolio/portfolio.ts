import { Component, inject, OnInit, signal, Input, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { DashboardService } from '../../dashboard/dashboard.service';

@Component({
  selector: 'app-portfolio',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './portfolio.html',
  styleUrls: ['./portfolio.css']
})
export class PortfolioComponent implements OnInit {
  private readonly router = inject(Router);
  private readonly dashboardService = inject(DashboardService);
  
  @Input() title = 'Open Positions';
  @Input() showViewAll = false;
  
  // Standalone page headers
  pageTitle = signal<string>('Your Positions');
  pageSubtitle = signal<string>('View and manage your open market positions.');
  isStandalone = signal<boolean>(false);
  
  // Bind to the centralized dashboard service state
  readonly positions = this.dashboardService.holdings;
  readonly portfolioSummary = this.dashboardService.portfolioSummary;
  readonly accountCurrency = this.dashboardService.accountCurrency;

  readonly enrichedPositions = computed(() => {
    const totalValue = this.portfolioSummary().currentHoldingsValue;
    return this.positions().map(p => ({
      ...p,
      allocation: totalValue > 0 ? (p.currentValue / totalValue) : 0
    }));
  });

  ngOnInit() {
    const url = this.router.url;
    this.isStandalone.set(url === '/portfolio');
    
    if (url === '/portfolio') {
      this.pageTitle.set('Your Portfolio');
      this.pageSubtitle.set('View and manage your market portfolio.');
      this.title = 'Portfolio'; // Update card title too
    }
    
    // Trigger data load if it's not already loaded
    // DashboardService handles the single source of truth
    if (this.positions().length === 0) {
      this.dashboardService.loadData();
    }
  }
}

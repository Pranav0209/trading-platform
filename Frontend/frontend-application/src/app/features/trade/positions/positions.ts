import { Component, inject, OnInit, signal, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { DashboardService } from '../../dashboard/dashboard.service';

@Component({
  selector: 'app-positions',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './positions.html',
  styleUrls: ['./positions.css']
})
export class PositionsComponent implements OnInit {
  private router = inject(Router);
  private dashboardService = inject(DashboardService);
  
  @Input() title = 'Open Positions';
  @Input() showViewAll = false;
  
  // Standalone page headers
  pageTitle = signal<string>('Your Positions');
  pageSubtitle = signal<string>('View and manage your open market positions.');
  isStandalone = signal<boolean>(false);
  
  // Bind to the centralized dashboard service state
  positions = this.dashboardService.holdings;
  accountCurrency = this.dashboardService.accountCurrency;

  ngOnInit() {
    const url = this.router.url;
    this.isStandalone.set(url === '/positions' || url === '/holdings');
    
    if (url === '/holdings') {
      this.pageTitle.set('Your Holdings');
      this.pageSubtitle.set('View and manage your market holdings.');
      this.title = 'Holdings'; // Update card title too
    }
    
    // Trigger data load if it's not already loaded
    // DashboardService handles the single source of truth
    if (this.positions().length === 0) {
      this.dashboardService.loadData();
    }
  }
}

import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PriceAlertsComponent } from '../../../core/trade/price-alerts/price-alerts';

@Component({
  selector: 'app-alerts',
  standalone: true,
  imports: [CommonModule, PriceAlertsComponent],
  template: `<app-price-alerts></app-price-alerts>`,
  styles: []
})
export class AlertsComponent {}

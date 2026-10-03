import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

type OrderStatus = 'NEW' | 'FILLED' | 'REJECTED' | 'CANCELLED';

interface StatusConfig {
  label: string;
  backgroundColor: string;
  color: string;
  textLabel: string; // Accessible text including status name
}

const STATUS_CONFIGS: Record<OrderStatus, StatusConfig> = {
  NEW: {
    label: 'NEW',
    backgroundColor: '#fff3cd', // Light yellow
    color: '#856404',           // Dark brown/yellow
    textLabel: 'Still Working' 
  },
  FILLED: {
    label: 'FILLED',
    backgroundColor: '#d4edda', // Light green
    color: '#155724',           // Dark green
    textLabel: 'Filled'
  },
  REJECTED: {
    label: 'REJECTED',
    backgroundColor: '#f8d7da', // Light red
    color: '#721c24',           // Dark red
    textLabel: 'Rejected'
  },
  CANCELLED: {
    label: 'CANCELLED',
    backgroundColor: '#e2e3e5', // Light gray
    color: '#383d41',           // Dark gray
    textLabel: 'Cancelled'
  }
};

@Component({
  selector: 'app-status-badge',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span 
      class="status-badge"
      [style.backgroundColor]="config.backgroundColor"
      [style.color]="config.color"
      [attr.data-testid]="'status-badge-' + status"
      [attr.aria-label]="config.textLabel"
      title="{{ config.textLabel }}"
    >
      {{ config.label }}
    </span>
  `,
  styles: [`
    .status-badge {
      display: inline-block;
      padding: 4px 12px;
      border-radius: 12px;
      font-size: 0.75rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      border: 1px solid currentColor;
      opacity: 0.8;
      white-space: nowrap;
    }
  `]
})
export class StatusBadgeComponent {
  @Input() status: OrderStatus = 'NEW';

  get config(): StatusConfig {
    return STATUS_CONFIGS[this.status] || STATUS_CONFIGS.NEW;
  }
}

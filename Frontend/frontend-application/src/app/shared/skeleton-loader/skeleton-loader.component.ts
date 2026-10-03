import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Generic skeleton loader component for loading states.
 * Shows a shimmer animation while data is being loaded.
 */
@Component({
  selector: 'app-skeleton-loader',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="skeleton-container">
      @switch (variant) {
        @case ('card') {
          <div class="skeleton-card">
            <div class="skeleton-header"></div>
            <div class="skeleton-line"></div>
            <div class="skeleton-line short"></div>
          </div>
        }
        @case ('table-row') {
          <div class="skeleton-table-row">
            <div class="skeleton-cell"></div>
            <div class="skeleton-cell"></div>
            <div class="skeleton-cell"></div>
            <div class="skeleton-cell"></div>
          </div>
        }
        @case ('text') {
          <div class="skeleton-line"></div>
        }
        @case ('circle') {
          <div class="skeleton-circle"></div>
        }
      }
    </div>
  `,
  styles: [`
    .skeleton-container {
      width: 100%;
    }

    .skeleton-card,
    .skeleton-table-row {
      display: flex;
      gap: 1rem;
      width: 100%;
    }

    .skeleton-header {
      height: 1.5rem;
      background: linear-gradient(
        90deg,
        #e0e0e0 25%,
        #f0f0f0 50%,
        #e0e0e0 75%
      );
      background-size: 200% 100%;
      border-radius: 0.25rem;
      animation: shimmer 2s infinite;
      width: 60%;
    }

    .skeleton-line {
      height: 1rem;
      background: linear-gradient(
        90deg,
        #e0e0e0 25%,
        #f0f0f0 50%,
        #e0e0e0 75%
      );
      background-size: 200% 100%;
      border-radius: 0.25rem;
      animation: shimmer 2s infinite;
      width: 100%;
      margin: 0.5rem 0;
    }

    .skeleton-line.short {
      width: 70%;
    }

    .skeleton-cell {
      height: 1.5rem;
      background: linear-gradient(
        90deg,
        #e0e0e0 25%,
        #f0f0f0 50%,
        #e0e0e0 75%
      );
      background-size: 200% 100%;
      border-radius: 0.25rem;
      animation: shimmer 2s infinite;
      flex: 1;
    }

    .skeleton-circle {
      width: 3rem;
      height: 3rem;
      border-radius: 50%;
      background: linear-gradient(
        90deg,
        #e0e0e0 25%,
        #f0f0f0 50%,
        #e0e0e0 75%
      );
      background-size: 200% 100%;
      animation: shimmer 2s infinite;
    }

    @keyframes shimmer {
      0% {
        background-position: -200% 0;
      }
      100% {
        background-position: calc(200% + 200px) 0;
      }
    }

    html.dark-theme .skeleton-header,
    html.dark-theme .skeleton-line,
    html.dark-theme .skeleton-cell,
    html.dark-theme .skeleton-circle {
      background: linear-gradient(
        90deg,
        #404040 25%,
        #505050 50%,
        #404040 75%
      );
    }
  `]
})
export class SkeletonLoaderComponent {
  @Input() variant: 'card' | 'table-row' | 'text' | 'circle' = 'text';
}

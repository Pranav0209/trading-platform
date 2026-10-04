import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AbstractControl } from '@angular/forms';

/**
 * Displays validation error messages for form controls.
 * Shows error only when field is touched and invalid.
 */
@Component({
  selector: 'app-form-error',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (control && control.touched && control.invalid) {
      <p class="field-error" role="alert">
        {{ message }}
      </p>
    }
  `,
  styles: [`
    .field-error {
      color: #c34131;
      font-size: 0.875rem;
      margin-top: 0.25rem;
      margin-bottom: 0;
    }

    html.dark-theme .field-error {
      color: #f48771;
    }
  `]
})
export class FormErrorComponent {
  @Input() control: AbstractControl | null = null;
  @Input() message: string = 'This field is invalid';
}

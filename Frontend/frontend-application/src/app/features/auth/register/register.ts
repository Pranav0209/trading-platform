import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../../generated/auth/api/auth.service';
import { ErrorCodeMapper } from '../../../core/error/error-code-mapper';
import { ToastService } from '../../../core/notifications/toast.service';
import { FormErrorComponent } from '../../../shared/form-error/form-error.component';
import {
  registerPasswordValidationMessage,
  registerPasswordValidators,
  usernameValidationMessage,
  usernameValidators,
} from '../auth-validation';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, FormErrorComponent],
  templateUrl: './register.html',
  styleUrls: ['../sign-in/sign-in.css']
})
export class Register {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);
  private errorCodeMapper = inject(ErrorCodeMapper);
  private toastService = inject(ToastService);

  protected readonly usernameValidationMessage = usernameValidationMessage;
  protected readonly passwordValidationMessage = registerPasswordValidationMessage;

  isSubmitting = signal(false);

  form = this.fb.nonNullable.group({
    username: ['', usernameValidators],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    password: ['', registerPasswordValidators],
  });

  onSubmit(): void {
    if (this.form.invalid) {
      this.toastService.warning('Please correct the form errors before submitting');
      return;
    }

    this.isSubmitting.set(true);
    const val = this.form.getRawValue();
    this.authService.register({
      username: val.username,
      email: val.email,
      password: val.password
    }).subscribe({
      next: (res: any) => {
        this.toastService.success('Account created successfully! Please sign in.');
        this.router.navigate(['/sign-in']);
      },
      error: (err: HttpErrorResponse) => {
        const errorMsg = this.errorCodeMapper.mapErrorToMessage(err);
        this.toastService.error(errorMsg);
        this.isSubmitting.set(false);
      }
    });
  }
}

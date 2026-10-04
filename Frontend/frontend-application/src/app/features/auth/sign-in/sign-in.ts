import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../../generated/auth/api/auth.service';
import { Session } from '../../../core/auth/session';
import { ErrorCodeMapper } from '../../../core/error/error-code-mapper';
import { sanitizeReturnTo } from '../../../core/auth/guards/return-to';
import { ToastService } from '../../../core/notifications/toast.service';
import { FormErrorComponent } from '../../../shared/form-error/form-error.component';

@Component({
  selector: 'app-sign-in',
  imports: [CommonModule, ReactiveFormsModule, RouterLink, FormErrorComponent],
  templateUrl: './sign-in.html',
  styleUrl: './sign-in.css',
})
export class SignIn {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly session = inject(Session);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly errorCodeMapper = inject(ErrorCodeMapper);
  private readonly toastService = inject(ToastService);

  form = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.maxLength(64)]],
    password: ['', [Validators.required, Validators.maxLength(128)]],
  });

  isLoading = signal(false);

  onSubmit() {
    if (this.form.invalid) {
      this.toastService.warning('Please correct the form errors before submitting');
      return;
    }

    this.isLoading.set(true);

    const username = this.form.get('username')?.value;
    const password = this.form.get('password')?.value;
    if (!username || !password) return;

    this.authService.login({ username, password }).subscribe({
      next: (res: any) => {
        this.session.setToken(res.accessToken);
        this.toastService.success('Signed in successfully!');
        this.navigateAfterLogin(res.accessToken);
        this.isLoading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        // Pass 'login' context so ERROR-401 shows "Invalid password" not "Session expired"
        // Backend returns AUTH-401 for both invalid credentials and throttle, but AUTH-429 for throttle
        const errorMsg = this.errorCodeMapper.mapErrorToMessage(error, 'login');
        this.toastService.error(errorMsg);
        this.isLoading.set(false);
      },
    });
  }

  private navigateAfterLogin(token: string) {
    const returnTo = sanitizeReturnTo(this.route.snapshot.queryParamMap?.get('returnTo'));

    if (returnTo) {
      this.router.navigateByUrl(returnTo);
      return;
    }

    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      if (payload.roles?.includes('ADMIN')) {
        this.router.navigate(['/admin']);
        return;
      }
    } catch (e) {
      console.error('Failed to parse token in sign-in', e);
    }
    
    // Standard User Redirect
    this.router.navigate(['/dashboard']);
  }
}

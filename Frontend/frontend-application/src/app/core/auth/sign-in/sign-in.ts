import { Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../../generated/auth/api/auth.service';
import { Session } from '../session';
import { ErrorCodeMapper } from '../../error/error-code-mapper';
import { sanitizeReturnTo } from '../guards/return-to';

@Component({
  selector: 'app-sign-in',
  imports: [ReactiveFormsModule, RouterLink],
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

  form = this.fb.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  errorMessage = signal<string | null>(null);
  isLoading = signal(false);

  onSubmit() {
    if (this.form.invalid) return;
    this.errorMessage.set(null);
    this.isLoading.set(true);

    const { username, password } = this.form.getRawValue();

    this.authService.login({ username, password }).subscribe({
      next: (res: any) => {
        this.session.setToken(res.accessToken);
        this.navigateAfterLogin(res.accessToken);
        this.isLoading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage.set(this.errorCodeMapper.mapErrorToMessage(error));
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

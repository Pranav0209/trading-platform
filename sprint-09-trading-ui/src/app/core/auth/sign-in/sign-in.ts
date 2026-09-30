import { Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../../generated/auth/api/auth.service';
import { Session } from '../session';
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

  form = this.fb.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  errorMessage = signal<string | null>(null);

  onSubmit() {
    if (this.form.invalid) return;
    this.errorMessage.set(null);

    this.authService.login(this.form.getRawValue()).subscribe({
      next: (res: any) => {
        this.session.setToken(res.accessToken);
        const returnTo = sanitizeReturnTo(this.route.snapshot.queryParamMap?.get('returnTo'));

        if (returnTo) {
          this.router.navigateByUrl(returnTo);
          return;
        }

        try {
          const payload = JSON.parse(atob(res.accessToken.split('.')[1]));
          if (payload.roles?.includes('ADMIN')) {
            this.router.navigate(['/admin']);
            return;
          }
        } catch (e) {
          console.error('Failed to parse token in sign-in', e);
        }
        this.router.navigate(['/']);
      },
      error: () => {
        this.errorMessage.set('Sign in failed. Please check your credentials and try again.');
      },
    });
  }
}

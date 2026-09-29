import { Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../../generated/auth/api/auth.service';
import { Session } from '../session';

@Component({
  selector: 'app-sign-in',
  imports: [ReactiveFormsModule],
  templateUrl: './sign-in.html',
  styleUrl: './sign-in.css',
})
export class SignIn {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private session = inject(Session);
  private router = inject(Router);

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
        this.router.navigate(['/']);
      },
      error: () => {
        this.errorMessage.set('Sign in failed. Please check your credentials and try again.');
      },
    });
  }
}

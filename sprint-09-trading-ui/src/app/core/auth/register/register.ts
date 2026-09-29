import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../../generated/auth/api/auth.service';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './register.html',
  styleUrls: ['../sign-in/sign-in.css']
})
export class Register {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);

  errorMessage = signal<string | null>(null);

  form = this.fb.nonNullable.group({
    username: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  onSubmit(): void {
    if (this.form.invalid) {
      return;
    }

    const val = this.form.getRawValue();
    this.authService.register({
      username: val.username,
      email: val.email,
      password: val.password
    }).subscribe({
      next: (res: any) => {
        this.router.navigate(['/sign-in']);
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 409) {
          this.errorMessage.set('That username or email is already taken.');
        } else if (err.status === 422) {
          this.errorMessage.set('A field is not acceptable.');
        } else if (err.status === 0) {
          this.errorMessage.set('Could not reach the server. Please check your connection.');
        } else {
          this.errorMessage.set('Registration failed. Please try again later.');
        }
      }
    });
  }
}

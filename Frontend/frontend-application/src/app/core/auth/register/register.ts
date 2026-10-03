import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../../generated/auth/api/auth.service';
import { ErrorCodeMapper } from '../../error/error-code-mapper';

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
  private errorCodeMapper = inject(ErrorCodeMapper);

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
        this.errorMessage.set(this.errorCodeMapper.mapErrorToMessage(err));
      }
    });
  }
}

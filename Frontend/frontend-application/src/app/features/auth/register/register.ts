import { Component, inject, signal, computed, OnDestroy } from '@angular/core';
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
  isOtpStep = signal(false);
  registeredEmail = signal('');

  form = this.fb.nonNullable.group({
    username: ['', usernameValidators],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    password: ['', registerPasswordValidators],
  });

  otpForm = this.fb.nonNullable.group({
    otpCode: ['', [Validators.required, Validators.minLength(6), Validators.maxLength(6)]],
  });

  // Timer state
  timerSeconds = signal(120);
  timerDisplay = computed(() => {
    const mins = Math.floor(this.timerSeconds() / 60);
    const secs = this.timerSeconds() % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  });
  private timerInterval: any;

  ngOnDestroy(): void {
    if (this.timerInterval) {
      clearInterval(this.timerInterval);
    }
  }

  private startTimer(): void {
    this.timerSeconds.set(120);
    if (this.timerInterval) clearInterval(this.timerInterval);
    
    this.timerInterval = setInterval(() => {
      if (this.timerSeconds() > 0) {
        this.timerSeconds.update(s => s - 1);
      } else {
        clearInterval(this.timerInterval);
      }
    }, 1000);
  }

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
        this.toastService.success('User created! Please check your email for the OTP.');
        this.registeredEmail.set(val.email);
        this.isOtpStep.set(true);
        this.isSubmitting.set(false);
        this.startTimer();
      },
      error: (err: HttpErrorResponse) => {
        const errorMsg = this.errorCodeMapper.mapErrorToMessage(err);
        this.toastService.error(errorMsg);
        this.isSubmitting.set(false);
      }
    });
  }

  onVerifyOtp(): void {
    if (this.otpForm.invalid) {
      this.toastService.warning('Please enter a valid 6-digit OTP');
      return;
    }

    this.isSubmitting.set(true);
    this.authService.verifyOtp({
      email: this.registeredEmail(),
      otpCode: this.otpForm.getRawValue().otpCode
    }).subscribe({
      next: () => {
        this.toastService.success('Account activated successfully! Please sign in.');
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

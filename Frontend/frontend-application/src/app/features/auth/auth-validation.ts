import { ValidatorFn, Validators } from '@angular/forms';

export const AUTH_USERNAME_PATTERN = /^[a-zA-Z0-9._-]+$/;
export const AUTH_USERNAME_MIN_LENGTH = 3;
export const AUTH_USERNAME_MAX_LENGTH = 50;
export const AUTH_PASSWORD_MIN_LENGTH = 8;
export const AUTH_PASSWORD_MAX_LENGTH = 128;
export const REGISTER_PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)\S+$/;

export const usernameValidators: ValidatorFn[] = [
  Validators.required,
  Validators.minLength(AUTH_USERNAME_MIN_LENGTH),
  Validators.maxLength(AUTH_USERNAME_MAX_LENGTH),
  Validators.pattern(AUTH_USERNAME_PATTERN),
];

export const loginPasswordValidators: ValidatorFn[] = [
  Validators.required,
  Validators.maxLength(AUTH_PASSWORD_MAX_LENGTH),
];

export const registerPasswordValidators: ValidatorFn[] = [
  Validators.required,
  Validators.minLength(AUTH_PASSWORD_MIN_LENGTH),
  Validators.maxLength(AUTH_PASSWORD_MAX_LENGTH),
  Validators.pattern(REGISTER_PASSWORD_PATTERN),
];

export const usernameValidationMessage =
  'Username must be 3-50 characters and use only letters, numbers, dots, underscores, or hyphens';

export const loginPasswordValidationMessage =
  'Password is required and must be 128 characters or less';

export const registerPasswordValidationMessage =
  'Password must be 8-128 characters, include uppercase and lowercase letters, include a number, and contain no spaces';
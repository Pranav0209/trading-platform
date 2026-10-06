import { FormControl } from '@angular/forms';
import {
  AUTH_PASSWORD_MAX_LENGTH,
  AUTH_USERNAME_MAX_LENGTH,
  loginPasswordValidators,
  registerPasswordValidators,
  usernameValidators,
} from './auth-validation';

describe('auth validation rules', () => {
  it('accepts a valid username', () => {
    const control = new FormControl('priya.menon', usernameValidators);

    expect(control.valid).toBe(true);
  });

  it('rejects usernames shorter than the minimum length', () => {
    const control = new FormControl('ab', usernameValidators);

    expect(control.hasError('minlength')).toBe(true);
  });

  it('rejects usernames with unsupported characters', () => {
    const control = new FormControl('priya menon', usernameValidators);

    expect(control.hasError('pattern')).toBe(true);
  });

  it('rejects usernames longer than the service limit', () => {
    const control = new FormControl('a'.repeat(AUTH_USERNAME_MAX_LENGTH + 1), usernameValidators);

    expect(control.hasError('maxlength')).toBe(true);
  });

  it('accepts a login password without strong-password checks', () => {
    const control = new FormControl('password123', loginPasswordValidators);

    expect(control.valid).toBe(true);
  });

  it('accepts a register password at the boundary lengths when it meets the rule', () => {
    const minimum = new FormControl('Password1', registerPasswordValidators);
    const maximum = new FormControl(
      `A${'a'.repeat(AUTH_PASSWORD_MAX_LENGTH - 2)}1`,
      registerPasswordValidators,
    );

    expect(minimum.valid).toBe(true);
    expect(maximum.valid).toBe(true);
  });

  it('rejects register passwords shorter than the minimum length', () => {
    const control = new FormControl('Short1', registerPasswordValidators);

    expect(control.hasError('minlength')).toBe(true);
  });

  it('rejects register passwords without an uppercase letter', () => {
    const control = new FormControl('password123', registerPasswordValidators);

    expect(control.hasError('pattern')).toBe(true);
  });

  it('rejects register passwords without a lowercase letter', () => {
    const control = new FormControl('PASSWORD123', registerPasswordValidators);

    expect(control.hasError('pattern')).toBe(true);
  });

  it('rejects register passwords without a number', () => {
    const control = new FormControl('PasswordOnly', registerPasswordValidators);

    expect(control.hasError('pattern')).toBe(true);
  });

  it('rejects register passwords containing spaces', () => {
    const control = new FormControl('Password 123', registerPasswordValidators);

    expect(control.hasError('pattern')).toBe(true);
  });

  it('rejects passwords longer than the maximum length', () => {
    const control = new FormControl(
      `A${'a'.repeat(AUTH_PASSWORD_MAX_LENGTH - 1)}1`,
      registerPasswordValidators,
    );

    expect(control.hasError('maxlength')).toBe(true);
  });
});
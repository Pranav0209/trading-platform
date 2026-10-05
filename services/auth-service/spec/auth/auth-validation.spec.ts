jest.mock('@nestjs/swagger', () => ({
  ApiProperty: () => () => undefined,
}));

import { validate } from 'class-validator';
import { LoginDto } from '../../src/auth/dto/login.dto';
import { RegisterDto } from '../../src/auth/dto/register.dto';

describe('auth DTO validation', () => {
  it('accepts valid login credentials', async () => {
    const dto = Object.assign(new LoginDto(), {
      username: 'priya.menon',
      password: 'password123',
    });

    await expect(validate(dto)).resolves.toHaveLength(0);
  });

  it('rejects login usernames shorter than three characters', async () => {
    const dto = Object.assign(new LoginDto(), {
      username: 'ab',
      password: 'password123',
    });

    const errors = await validate(dto);

    expect(errors.some((error) => error.property === 'username')).toBe(true);
  });

  it('rejects login usernames with unsupported characters', async () => {
    const dto = Object.assign(new LoginDto(), {
      username: 'priya menon',
      password: 'password123',
    });

    const errors = await validate(dto);

    expect(errors.some((error) => error.property === 'username')).toBe(true);
  });

  it('allows login passwords that do not meet the registration complexity rule', async () => {
    const dto = Object.assign(new LoginDto(), {
      username: 'priya.menon',
      password: 'password123',
    });

    await expect(validate(dto)).resolves.toHaveLength(0);
  });

  it('rejects registration usernames longer than fifty characters', async () => {
    const dto = Object.assign(new RegisterDto(), {
      username: 'a'.repeat(51),
      email: 'priya.menon@example.com',
      password: 'password123',
    });

    const errors = await validate(dto);

    expect(errors.some((error) => error.property === 'username')).toBe(true);
  });

  it('rejects registration passwords longer than one hundred twenty-eight characters', async () => {
    const dto = Object.assign(new RegisterDto(), {
      username: 'priya.menon',
      email: 'priya.menon@example.com',
      password: 'a'.repeat(129),
    });

    const errors = await validate(dto);

    expect(errors.some((error) => error.property === 'password')).toBe(true);
  });

  it('rejects registration passwords without an uppercase letter, lowercase letter, and number', async () => {
    const dto = Object.assign(new RegisterDto(), {
      username: 'priya.menon',
      email: 'priya.menon@example.com',
      password: 'passwordonly',
    });

    const errors = await validate(dto);

    expect(errors.some((error) => error.property === 'password')).toBe(true);
  });

  it('rejects registration passwords containing spaces', async () => {
    const dto = Object.assign(new RegisterDto(), {
      username: 'priya.menon',
      email: 'priya.menon@example.com',
      password: 'Password 123',
    });

    const errors = await validate(dto);

    expect(errors.some((error) => error.property === 'password')).toBe(true);
  });
});
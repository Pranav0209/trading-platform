import { JwtService } from '@nestjs/jwt';
import * as bcrypt from 'bcryptjs';
import { AuthService } from './auth.service';

describe('AuthService token contract', () => {
  const jwtSecret = 'unit-test-secret-that-is-at-least-32-chars';

  let service: AuthService;
  let jwtService: JwtService;

  const usersService = {
    findOneByUsername: jest.fn(),
    getAccountIdForUser: jest.fn(),
  };

  const configService = {
    get: jest.fn((key: string) => {
      if (key === 'JWT_SECRET') {
        return jwtSecret;
      }
      if (key === 'JWT_ISSUER') {
        return 'auth-service';
      }
      return undefined;
    }),
  };

  const refreshTokenRepository = {
    create: jest.fn((entity) => entity),
    save: jest.fn(),
  };

  const user = {
    id: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
    username: 'priya.menon',
    passwordHash: '$2a$12$B2j95Yx6fM7JiPTHM0hJVO2x1AW.KV2kR/igjOc4zYh8PB0L5Y8QW',
    roles: ['CUSTOMER'],
    createdAt: new Date(),
  };

  beforeEach(() => {
    jest.clearAllMocks();

    jwtService = new JwtService();
    service = new AuthService(
      usersService as any,
      jwtService,
      configService as any,
      refreshTokenRepository as any,
    );

    usersService.findOneByUsername.mockResolvedValue(user);
    usersService.getAccountIdForUser.mockResolvedValue(1);
    refreshTokenRepository.save.mockResolvedValue(undefined);

    jest.spyOn(bcrypt, 'compare').mockResolvedValue(true as never);
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('issues a token with contract claims and 15-minute expiry', async () => {
    const result = await service.login({
      username: 'priya.menon',
      password: 'correct horse battery staple',
    });

    const payload = jwtService.verify(result.accessToken, {
      secret: jwtSecret,
      algorithms: ['HS256'],
    }) as Record<string, unknown>;

    expect(payload.sub).toBe(user.id);
    expect(payload.accountId).toBe(1);
    expect(payload.roles).toEqual(['CUSTOMER']);
    expect(payload.iss).toBe('auth-service');
    expect(typeof payload.iat).toBe('number');
    expect(typeof payload.exp).toBe('number');
    expect((payload.exp as number) - (payload.iat as number)).toBe(900);
  });

  it('signs the token so the service key verifies the signature', async () => {
    const result = await service.login({
      username: 'priya.menon',
      password: 'correct horse battery staple',
    });

    expect(() => {
      jwtService.verify(result.accessToken, {
        secret: jwtSecret,
        algorithms: ['HS256'],
      });
    }).not.toThrow();
  });

  it('includes no claim outside the contract set', async () => {
    usersService.getAccountIdForUser.mockResolvedValue('1');

    const result = await service.login({
      username: 'priya.menon',
      password: 'correct horse battery staple',
    });

    const payload = jwtService.verify(result.accessToken, {
      secret: jwtSecret,
      algorithms: ['HS256'],
    }) as Record<string, unknown>;

    expect(Object.keys(payload).sort()).toEqual(
      ['sub', 'accountId', 'roles', 'iat', 'exp', 'iss'].sort(),
    );
    expect(typeof payload.accountId).toBe('number');
  });
});

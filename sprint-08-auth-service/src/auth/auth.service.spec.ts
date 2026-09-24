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

describe('Requirement 6: Refresh Token Issuance and Rotation', () => {
  const jwtSecret = 'unit-test-secret-that-is-at-least-32-chars';
  const crypto = require('crypto');

  let service: AuthService;
  let jwtService: JwtService;

  const usersService = {
    findOneByUsername: jest.fn(),
    findOneById: jest.fn(),
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

  const user = {
    id: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
    username: 'priya.menon',
    passwordHash: '$2a$12$B2j95Yx6fM7JiPTHM0hJVO2x1AW.KV2kR/igjOc4zYh8PB0L5Y8QW',
    roles: ['CUSTOMER'],
    createdAt: new Date(),
  };

  let refreshTokenRepository: any;
  let storedTokens: Map<string, any>;

  beforeEach(() => {
    jest.clearAllMocks();

    // Create a mock repository that simulates database behavior
    storedTokens = new Map();

    refreshTokenRepository = {
      create: jest.fn((entity) => entity),
      save: jest.fn((entity) => {
        // Store tokens in our mock
        storedTokens.set(entity.tokenHash, entity);
        return Promise.resolve(entity);
      }),
      findOne: jest.fn((options: any) => {
        const hash = options.where.tokenHash;
        const token = storedTokens.get(hash);
        if (token) {
          return Promise.resolve({
            ...token,
            user,
          });
        }
        return Promise.resolve(null);
      }),
      update: jest.fn((where: any, data: any) => {
        // Revoke all tokens for a user
        storedTokens.forEach((token) => {
          if (token.userId === where.userId) {
            token.revoked = true;
          }
        });
        return Promise.resolve({ affected: 1 });
      }),
    };

    jwtService = new JwtService();
    service = new AuthService(
      usersService as any,
      jwtService,
      configService as any,
      refreshTokenRepository as any,
    );

    usersService.findOneById.mockResolvedValue(user);
    usersService.getAccountIdForUser.mockResolvedValue(1);
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  /**
   * Requirement 6: Refresh Token Issuance and Rotation
   * Unit Test Execution Path: Refresh returns a new access token and a new refresh token
   * Acceptance Criteria: Every refresh issues a new refresh token
   */
  it('should return a new access token and a new refresh token on refresh', async () => {
    // First, login to get initial tokens
    usersService.findOneByUsername = jest.fn().mockResolvedValue(user);
    jest.spyOn(bcrypt, 'compare').mockResolvedValue(true as never);

    const loginResult = await service.login({
      username: 'priya.menon',
      password: 'correct horse battery staple',
    });

    const firstRefreshToken = loginResult.refreshToken;
    const firstAccessToken = loginResult.accessToken;

    // Now refresh with the token we got
    const refreshResult = await service.refresh({
      refreshToken: firstRefreshToken,
    });

    // Verify we get new tokens
    expect(refreshResult.accessToken).toBeDefined();
    expect(refreshResult.refreshToken).toBeDefined();

    // Tokens should be different
    expect(refreshResult.accessToken).not.toBe(firstAccessToken);
    expect(refreshResult.refreshToken).not.toBe(firstRefreshToken);

    // Both should be valid tokens
    expect(refreshResult.tokenType).toBe('Bearer');
    expect(refreshResult.expiresIn).toBe(900);

    // New access token should have valid claims
    const newPayload = jwtService.verify(refreshResult.accessToken, {
      secret: jwtSecret,
      algorithms: ['HS256'],
    }) as Record<string, unknown>;

    expect(newPayload.sub).toBe(user.id);
    expect(newPayload.accountId).toBe(1);
  });

  /**
   * Requirement 6: Refresh Token Issuance and Rotation
   * Unit Test Execution Path: The newly issued refresh token works
   */
  it('should accept the newly issued refresh token from a previous refresh', async () => {
    // Login
    usersService.findOneByUsername = jest.fn().mockResolvedValue(user);
    jest.spyOn(bcrypt, 'compare').mockResolvedValue(true as never);

    const loginResult = await service.login({
      username: 'priya.menon',
      password: 'correct horse battery staple',
    });

    // First refresh
    const firstRefreshResult = await service.refresh({
      refreshToken: loginResult.refreshToken,
    });

    const newRefreshToken = firstRefreshResult.refreshToken;

    // Use the newly issued refresh token to refresh again
    const secondRefreshResult = await service.refresh({
      refreshToken: newRefreshToken,
    });

    // Should succeed and return new tokens
    expect(secondRefreshResult.accessToken).toBeDefined();
    expect(secondRefreshResult.refreshToken).toBeDefined();

    // Verify the new access token is valid
    const payload = jwtService.verify(secondRefreshResult.accessToken, {
      secret: jwtSecret,
      algorithms: ['HS256'],
    }) as Record<string, unknown>;

    expect(payload.sub).toBe(user.id);
    expect(payload.accountId).toBe(1);
  });

  /**
   * Requirement 6: Refresh Token Issuance and Rotation
   * Acceptance Criteria: Where revocation of the presented token is built, a second presentation answers AUTH-401
   * Unit Test Execution Path: The declared revocation behaviour holds for a token that has already been exchanged
   */
  it('should revoke the presented token and refuse second use', async () => {
    const { UnauthorizedException } = require('@nestjs/common');

    // Login
    usersService.findOneByUsername = jest.fn().mockResolvedValue(user);
    jest.spyOn(bcrypt, 'compare').mockResolvedValue(true as never);

    const loginResult = await service.login({
      username: 'priya.menon',
      password: 'correct horse battery staple',
    });

    const firstRefreshToken = loginResult.refreshToken;

    // First refresh succeeds
    const firstRefreshResult = await service.refresh({
      refreshToken: firstRefreshToken,
    });

    expect(firstRefreshResult.accessToken).toBeDefined();
    expect(firstRefreshResult.refreshToken).toBeDefined();

    // Try to use the same refresh token again - should fail
    try {
      await service.refresh({
        refreshToken: firstRefreshToken,
      });
      fail('Should have thrown UnauthorizedException');
    } catch (error) {
      expect(error).toBeInstanceOf(UnauthorizedException);
      expect(error.response).toMatchObject({
        errorCode: 'AUTH-401',
        message: 'Unauthorised',
      });
    }
  });

  /**
   * Requirement 6: Refresh Token Issuance and Rotation
   * Notes: Reissue alone defends against a stolen token used once. What is missing without revocation
   * is that the old value still works, so both parties hold live sessions and nothing in the service
   * can tell there are two of them.
   *
   * This test verifies that revocation is implemented: when a revoked token is presented,
   * all tokens for that user are revoked (detecting token theft).
   */
  it('should detect token theft and revoke all tokens when a revoked token is used', async () => {
    const { UnauthorizedException } = require('@nestjs/common');

    // Login
    usersService.findOneByUsername = jest.fn().mockResolvedValue(user);
    jest.spyOn(bcrypt, 'compare').mockResolvedValue(true as never);

    const loginResult = await service.login({
      username: 'priya.menon',
      password: 'correct horse battery staple',
    });

    const refreshTokenHash = crypto
      .createHash('sha256')
      .update(loginResult.refreshToken)
      .digest('hex');

    // Manually mark the token as revoked (simulating a re-use attempt after theft detection)
    const storedToken = storedTokens.get(refreshTokenHash);
    if (storedToken) {
      storedToken.revoked = true;
    }

    // Try to use the revoked token - should fail and trigger mass revocation
    try {
      await service.refresh({
        refreshToken: loginResult.refreshToken,
      });
      fail('Should have thrown UnauthorizedException');
    } catch (error) {
      expect(error).toBeInstanceOf(UnauthorizedException);
      expect(error.response).toMatchObject({
        errorCode: 'AUTH-401',
        message: 'Unauthorised',
      });

      // Verify that update was called to revoke all tokens for this user
      expect(refreshTokenRepository.update).toHaveBeenCalledWith(
        { userId: user.id },
        { revoked: true },
      );
    }
  });

  /**
   * Requirement 6: Refresh Token Issuance and Rotation
   * Acceptance Criteria: Store a hash of the refresh token rather than the token,
   * so that read access to your database is not session takeover.
   */
  it('should store a hash of the refresh token, not the token itself', async () => {
    usersService.findOneByUsername = jest.fn().mockResolvedValue(user);
    jest.spyOn(bcrypt, 'compare').mockResolvedValue(true as never);

    const loginResult = await service.login({
      username: 'priya.menon',
      password: 'correct horse battery staple',
    });

    // Check that the saved token is a hash, not the raw token
    const savedCall = refreshTokenRepository.save.mock.calls[0][0];

    expect(savedCall.tokenHash).toBeDefined();
    expect(savedCall.tokenHash).not.toBe(loginResult.refreshToken);

    // Verify it's a hash format (64 hex chars for SHA256)
    expect(savedCall.tokenHash).toMatch(/^[a-f0-9]{64}$/);

    // Verify the hash matches what we'd compute from the token
    const computedHash = crypto
      .createHash('sha256')
      .update(loginResult.refreshToken)
      .digest('hex');

    expect(savedCall.tokenHash).toBe(computedHash);
  });

  /**
   * Requirement 6: Refresh Token Issuance and Rotation
   * Acceptance Criteria: Every refresh issues a new refresh token
   */
  it('should issue a unique refresh token every time', async () => {
    usersService.findOneByUsername = jest.fn().mockResolvedValue(user);
    jest.spyOn(bcrypt, 'compare').mockResolvedValue(true as never);

    const loginResult = await service.login({
      username: 'priya.menon',
      password: 'correct horse battery staple',
    });

    const tokens = [loginResult.refreshToken];

    // Perform multiple refreshes and collect all tokens
    let currentToken = loginResult.refreshToken;
    for (let i = 0; i < 3; i++) {
      const refreshResult = await service.refresh({
        refreshToken: currentToken,
      });

      tokens.push(refreshResult.refreshToken);
      currentToken = refreshResult.refreshToken;
    }

    // All tokens should be unique
    const uniqueTokens = new Set(tokens);
    expect(uniqueTokens.size).toBe(tokens.length);
  });
});

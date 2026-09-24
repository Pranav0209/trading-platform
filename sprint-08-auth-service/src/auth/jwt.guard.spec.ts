import { Test, TestingModule } from '@nestjs/testing';
import { ExecutionContext, UnauthorizedException } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { JwtAuthGuard } from './jwt.guard';

describe('JwtAuthGuard - Token Verification on Protected Route', () => {
  const jwtSecret = 'unit-test-secret-that-is-at-least-32-chars';
  const wrongSecret = 'wrong-secret-that-is-at-least-32-chars';

  let guard: JwtAuthGuard;
  let jwtService: JwtService;

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

  const validPayload = {
    sub: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
    accountId: 1,
    roles: ['CUSTOMER'],
    iss: 'auth-service',
    iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + 900,
  };

  beforeEach(async () => {
    jest.clearAllMocks();

    jwtService = new JwtService();
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        JwtAuthGuard,
        {
          provide: JwtService,
          useValue: jwtService,
        },
        {
          provide: 'ConfigService',
          useValue: configService,
        },
      ],
    }).compile();

    guard = new JwtAuthGuard(jwtService, configService as any);
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  describe('Requirement 5: Guard and Token Verification on Protected Route', () => {
    /**
     * Unit Test Execution Path: A valid token is accepted
     */
    it('should accept a valid token with correct signature and claims', async () => {
      const token = jwtService.sign(validPayload, {
        secret: jwtSecret,
        algorithm: 'HS256',
        noTimestamp: false,
      });

      const mockRequest = {
        headers: {
          authorization: `Bearer ${token}`,
        },
        user: null,
      };

      const mockExecutionContext = {
        switchToHttp: () => ({
          getRequest: () => mockRequest,
        }),
      } as unknown as ExecutionContext;

      const result = await guard.canActivate(mockExecutionContext);

      expect(result).toBe(true);
      expect(mockRequest.user).toBeDefined();
      expect(mockRequest.user.sub).toBe(validPayload.sub);
      expect(mockRequest.user.accountId).toBe(validPayload.accountId);
    });

    /**
     * Unit Test Execution Path: An expired token is refused
     * Acceptance Criteria: The protected route is guarded, and an expired token is refused with AUTH-401
     */
    it('should refuse an expired token with AUTH-401', async () => {
      // Build the expired case by signing a genuine token with a past expiry
      const expiredPayload = {
        ...validPayload,
        iat: Math.floor(Date.now() / 1000) - 1800, // 30 minutes ago
        exp: Math.floor(Date.now() / 1000) - 900, // 15 minutes ago (already expired)
      };

      const expiredToken = jwtService.sign(expiredPayload, {
        secret: jwtSecret,
        algorithm: 'HS256',
        noTimestamp: true,
      });

      const mockRequest = {
        headers: {
          authorization: `Bearer ${expiredToken}`,
        },
      };

      const mockExecutionContext = {
        switchToHttp: () => ({
          getRequest: () => mockRequest,
        }),
      } as unknown as ExecutionContext;

      try {
        await guard.canActivate(mockExecutionContext);
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
     * Unit Test Execution Path: A token with wrong signature is refused
     * Acceptance Criteria: A tampered token is refused with AUTH-401
     * Build the wrong-signature case by signing a genuine, unexpired token with a different key
     */
    it('should refuse a token with wrong signature with AUTH-401 before reading claims', async () => {
      // Sign the token with a different secret (genuinely signed, but with wrong key)
      const wronglySignedToken = jwtService.sign(validPayload, {
        secret: wrongSecret,
        algorithm: 'HS256',
        noTimestamp: false,
      });

      const mockRequest = {
        headers: {
          authorization: `Bearer ${wronglySignedToken}`,
        },
      };

      const mockExecutionContext = {
        switchToHttp: () => ({
          getRequest: () => mockRequest,
        }),
      } as unknown as ExecutionContext;

      try {
        await guard.canActivate(mockExecutionContext);
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
     * Unit Test Execution Path: A malformed header is refused
     */
    it('should refuse a request with malformed header (missing Bearer scheme)', async () => {
      const mockRequest = {
        headers: {
          authorization: 'InvalidScheme token-here',
        },
      };

      const mockExecutionContext = {
        switchToHttp: () => ({
          getRequest: () => mockRequest,
        }),
      } as unknown as ExecutionContext;

      try {
        await guard.canActivate(mockExecutionContext);
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
     * Unit Test Execution Path: A malformed header is refused
     */
    it('should refuse a request with no authorization header', async () => {
      const mockRequest = {
        headers: {},
      };

      const mockExecutionContext = {
        switchToHttp: () => ({
          getRequest: () => mockRequest,
        }),
      } as unknown as ExecutionContext;

      try {
        await guard.canActivate(mockExecutionContext);
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
     * Note: A guard that checks the signature and forgets the clock accepts every token it ever issued, for ever.
     * This test verifies that our implementation DOES NOT have this vulnerability.
     */
    it('should verify token signature AND expiry together', async () => {
      // Create two tokens: one valid, one expired
      const validToken = jwtService.sign(validPayload, {
        secret: jwtSecret,
        algorithm: 'HS256',
        noTimestamp: false,
      });

      const expiredPayload = {
        ...validPayload,
        exp: Math.floor(Date.now() / 1000) - 1, // Expired 1 second ago
      };

      const expiredToken = jwtService.sign(expiredPayload, {
        secret: jwtSecret,
        algorithm: 'HS256',
        noTimestamp: true,
      });

      // Valid token should pass
      const validRequest = {
        headers: {
          authorization: `Bearer ${validToken}`,
        },
        user: null,
      };

      const validContext = {
        switchToHttp: () => ({
          getRequest: () => validRequest,
        }),
      } as unknown as ExecutionContext;

      const validResult = await guard.canActivate(validContext);
      expect(validResult).toBe(true);

      // Expired token should fail even though signature is correct
      const expiredRequest = {
        headers: {
          authorization: `Bearer ${expiredToken}`,
        },
      };

      const expiredContext = {
        switchToHttp: () => ({
          getRequest: () => expiredRequest,
        }),
      } as unknown as ExecutionContext;

      try {
        await guard.canActivate(expiredContext);
        fail('Should have thrown UnauthorizedException for expired token');
      } catch (error) {
        expect(error).toBeInstanceOf(UnauthorizedException);
        expect(error.response).toMatchObject({
          errorCode: 'AUTH-401',
          message: 'Unauthorised',
        });
      }
    });
  });
});

jest.mock('@nestjs/common', () => ({
  Injectable: () => (target: unknown) => target,
  UnauthorizedException: class UnauthorizedException extends Error {
    constructor(public readonly response: unknown) {
      super('Unauthorised');
      this.name = 'UnauthorizedException';
    }
  },
}));

jest.mock('@nestjs/jwt', () => ({
  JwtService: class JwtService {},
}));

const { JwtAuthGuard } = require('../../../src/auth/guards/jwt.guard') as {
  JwtAuthGuard: new (jwtService: unknown) => {
    canActivate(context: unknown): Promise<boolean>;
  };
};

const { UnauthorizedException } = require('@nestjs/common') as {
  UnauthorizedException: new (...args: unknown[]) => Error;
};

describe('JwtAuthGuard', () => {
  const originalSecret = process.env.JWT_SECRET;

  let jwtService: { verifyAsync: jest.Mock };
  let guard: InstanceType<typeof JwtAuthGuard>;

  beforeEach(() => {
    process.env.JWT_SECRET = 'test-secret';

    jwtService = {
      verifyAsync: jest.fn(),
    };

    guard = new JwtAuthGuard(jwtService as never);
  });

  afterEach(() => {
    jest.restoreAllMocks();
    if (originalSecret === undefined) {
      delete process.env.JWT_SECRET;
      return;
    }
    process.env.JWT_SECRET = originalSecret;
  });

  const createExecutionContext = (request: any) =>
    ({
      switchToHttp: () => ({
        getRequest: () => request,
      }),
    }) as any;

  it('throws UnauthorizedException when authorization header is missing', async () => {
    const request = { headers: {} };
    const context = createExecutionContext(request);

    await expect(guard.canActivate(context)).rejects.toThrow(UnauthorizedException);
    expect(jwtService.verifyAsync).not.toHaveBeenCalled();
  });

  it('throws UnauthorizedException when auth type is not Bearer', async () => {
    const request = { headers: { authorization: 'Basic token' } };
    const context = createExecutionContext(request);

    await expect(guard.canActivate(context)).rejects.toThrow(UnauthorizedException);
    expect(jwtService.verifyAsync).not.toHaveBeenCalled();
  });

  it('throws UnauthorizedException when token verification fails', async () => {
    const request = { headers: { authorization: 'Bearer invalid-token' } };
    const context = createExecutionContext(request);
    jwtService.verifyAsync.mockRejectedValue(new Error('invalid token'));

    await expect(guard.canActivate(context)).rejects.toThrow(UnauthorizedException);
    expect(jwtService.verifyAsync).toHaveBeenCalledWith('invalid-token', {
      secret: 'test-secret',
    });
    expect((request as any).user).toBeUndefined();
  });

  it('returns true and sets request user when token verification succeeds', async () => {
    const request = { headers: { authorization: 'Bearer valid-token' } };
    const context = createExecutionContext(request);
    const payload = { sub: 'alice', userId: 1, role: 'CUSTOMER' };
    jwtService.verifyAsync.mockResolvedValue(payload);

    await expect(guard.canActivate(context)).resolves.toBe(true);
    expect(jwtService.verifyAsync).toHaveBeenCalledWith('valid-token', {
      secret: 'test-secret',
    });
    expect((request as any).user).toEqual(payload);
  });
});

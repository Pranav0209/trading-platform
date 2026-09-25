import * as bcrypt from 'bcryptjs';
import * as crypto from 'crypto';

jest.mock('bcryptjs', () => ({
  genSalt: jest.fn(),
  hash: jest.fn(),
  compare: jest.fn(),
}));

jest.mock('@nestjs/common', () => ({
  Injectable: () => (target: unknown) => target,
  UnauthorizedException: class UnauthorizedException extends Error {
    constructor(public readonly response: unknown) {
      super('Unauthorised');
      this.name = 'UnauthorizedException';
    }
  },
  ConflictException: class ConflictException extends Error {
    constructor(public readonly response: unknown) {
      super('Registration failed');
      this.name = 'ConflictException';
    }
  },
}));

jest.mock('@nestjs/typeorm', () => ({
  InjectRepository: () => () => undefined,
}));

jest.mock('@nestjs/jwt', () => ({
  JwtService: class JwtService {},
}));

const { AuthService } = require('../../src/auth/auth.service') as {
  AuthService: new (
    usersService: unknown,
    jwtService: unknown,
    loginThrottleService: unknown,
    refreshTokenRepository: unknown,
  ) => unknown;
};

const { ConflictException, UnauthorizedException } = require('@nestjs/common') as {
  ConflictException: new (...args: unknown[]) => Error;
  UnauthorizedException: new (...args: unknown[]) => Error;
};

describe('AuthService', () => {
  let service: InstanceType<typeof AuthService>;
  let mockedBcrypt: {
    genSalt: jest.Mock;
    hash: jest.Mock;
    compare: jest.Mock;
  };

  let usersService: {
    findOneByUsername: jest.Mock;
    findOneByEmail: jest.Mock;
    create: jest.Mock;
    findOneById: jest.Mock;
  };

  let jwtService: {
    signAsync: jest.Mock;
  };

  let loginThrottleService: {
    isThrottled: jest.Mock;
    recordFailedAttempt: jest.Mock;
    recordSuccessfulAttempt: jest.Mock;
  };

  let refreshTokenRepository: {
    findOne: jest.Mock;
    update: jest.Mock;
    save: jest.Mock;
    create: jest.Mock;
    query: jest.Mock;
  };

  beforeEach(() => {
    mockedBcrypt = bcrypt as unknown as {
      genSalt: jest.Mock;
      hash: jest.Mock;
      compare: jest.Mock;
    };

    usersService = {
      findOneByUsername: jest.fn(),
      findOneByEmail: jest.fn(),
      create: jest.fn(),
      findOneById: jest.fn(),
    };

    jwtService = {
      signAsync: jest.fn().mockResolvedValue('signed-access-token'),
    };

    loginThrottleService = {
      isThrottled: jest.fn().mockResolvedValue(false),
      recordFailedAttempt: jest.fn().mockResolvedValue(undefined),
      recordSuccessfulAttempt: jest.fn().mockResolvedValue(undefined),
    };

    refreshTokenRepository = {
      findOne: jest.fn(),
      update: jest.fn().mockResolvedValue(undefined),
      save: jest.fn().mockImplementation(async (value) => value),
      create: jest.fn().mockImplementation((value) => value),
      query: jest.fn().mockResolvedValue([{ id: '44' }]),
    };

    service = new AuthService(
      usersService as never,
      jwtService as never,
      loginThrottleService as never,
      refreshTokenRepository as never,
    );

    mockedBcrypt.genSalt.mockResolvedValue('salt-12');
    mockedBcrypt.hash.mockResolvedValue('hashed-password');
    mockedBcrypt.compare.mockResolvedValue(true);
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('registers a new user when username and email are available', async () => {
    usersService.findOneByUsername.mockResolvedValue(null);
    usersService.findOneByEmail.mockResolvedValue(null);
    usersService.create.mockResolvedValue({
      id: 7,
      username: 'carol',
      email: 'carol@mission.local',
      role: 'CUSTOMER',
      createdAt: new Date('2026-09-24T12:00:00.000Z'),
    });

    const result = await service.register({
      username: 'carol',
      email: 'carol@mission.local',
      password: 'mission123',
    });

    expect(usersService.create).toHaveBeenCalledWith({
      username: 'carol',
      email: 'carol@mission.local',
      passwordHash: 'hashed-password',
      role: 'CUSTOMER',
      status: 'ACTIVE',
    });
    expect(result).toEqual({
      id: '7',
      username: 'carol',
      email: 'carol@mission.local',
      roles: ['CUSTOMER'],
      createdOn: new Date('2026-09-24T12:00:00.000Z'),
    });
  });

  it('rejects registration when username already exists', async () => {
    usersService.findOneByUsername.mockResolvedValue({ id: 1 });
    usersService.findOneByEmail.mockResolvedValue(null);

    await expect(
      service.register({ username: 'carol', email: 'carol@mission.local', password: 'mission123' }),
    ).rejects.toThrow(ConflictException);
  });

  it('rejects registration when email already exists', async () => {
    usersService.findOneByUsername.mockResolvedValue(null);
    usersService.findOneByEmail.mockResolvedValue({ id: 1 });

    await expect(
      service.register({ username: 'carol', email: 'carol@mission.local', password: 'mission123' }),
    ).rejects.toThrow(ConflictException);
  });

  it('logs in successfully and returns token bundle', async () => {
    usersService.findOneByUsername.mockResolvedValue({
      id: 11,
      username: 'carol',
      passwordHash: 'stored-hash',
      role: 'MISSION_OPERATOR',
    });
    refreshTokenRepository.query.mockResolvedValue([{ id: '501' }]);

    const result = await service.login({ username: 'carol', password: 'mission123' }, '10.0.0.1');

    expect(loginThrottleService.recordSuccessfulAttempt).toHaveBeenCalledWith('carol', '10.0.0.1');
    expect(jwtService.signAsync).toHaveBeenCalledWith(
      expect.objectContaining({
        sub: 'carol',
        userId: 11,
        role: 'MISSION_OPERATOR',
        accountId: 501,
        roles: ['MISSION_OPERATOR'],
        iss: 'auth-service',
      }),
    );
    expect(result.accessToken).toBe('signed-access-token');
    expect(result.tokenType).toBe('Bearer');
    expect(result.expiresIn).toBe(900);
    expect(typeof result.refreshToken).toBe('string');
    expect(result.refreshToken).toHaveLength(64);
    expect(refreshTokenRepository.create).toHaveBeenCalledWith(
      expect.objectContaining({
        userId: 11,
        revoked: false,
      }),
    );
  });

  it('rejects login when throttled even if credentials are valid', async () => {
    loginThrottleService.isThrottled.mockResolvedValue(true);
    usersService.findOneByUsername.mockResolvedValue({
      id: 11,
      username: 'carol',
      passwordHash: 'stored-hash',
      role: 'MISSION_OPERATOR',
    });

    await expect(
      service.login({ username: 'carol', password: 'mission123' }, '10.0.0.1'),
    ).rejects.toThrow(UnauthorizedException);

    expect(loginThrottleService.recordFailedAttempt).toHaveBeenCalledWith('carol', '10.0.0.1');
  });

  it('rejects login when password is incorrect', async () => {
    mockedBcrypt.compare.mockResolvedValueOnce(false);
    usersService.findOneByUsername.mockResolvedValue({
      id: 11,
      username: 'carol',
      passwordHash: 'stored-hash',
      role: 'MISSION_OPERATOR',
    });

    await expect(service.login({ username: 'carol', password: 'wrong' })).rejects.toThrow(
      UnauthorizedException,
    );

    expect(loginThrottleService.recordFailedAttempt).toHaveBeenCalledWith('carol', '0.0.0.0');
  });

  it('rejects login for unknown user and executes dummy compare path', async () => {
    usersService.findOneByUsername.mockResolvedValue(null);
    mockedBcrypt.compare.mockRejectedValueOnce(new Error('dummy compare failed'));

    await expect(service.login({ username: 'missing', password: 'any' })).rejects.toThrow(
      UnauthorizedException,
    );

    expect(loginThrottleService.recordFailedAttempt).toHaveBeenCalledWith('missing', '0.0.0.0');
  });

  it('refresh rejects when token is not found', async () => {
    refreshTokenRepository.findOne.mockResolvedValue(null);

    await expect(service.refresh({ refreshToken: 'missing-token' })).rejects.toThrow(
      UnauthorizedException,
    );
  });

  it('refresh rejects when token is expired', async () => {
    refreshTokenRepository.findOne.mockResolvedValue({
      userId: 11,
      expiresAt: new Date(Date.now() - 1_000),
      revoked: false,
      user: { id: 11, username: 'carol', role: 'MISSION_OPERATOR' },
    });

    await expect(service.refresh({ refreshToken: 'expired-token' })).rejects.toThrow(
      UnauthorizedException,
    );
  });

  it('refresh detects revoked token theft and revokes all sessions', async () => {
    refreshTokenRepository.findOne.mockResolvedValue({
      userId: 11,
      expiresAt: new Date(Date.now() + 60_000),
      revoked: true,
      user: { id: 11, username: 'carol', role: 'MISSION_OPERATOR' },
    });

    await expect(service.refresh({ refreshToken: 'revoked-token' })).rejects.toThrow(
      UnauthorizedException,
    );

    expect(refreshTokenRepository.update).toHaveBeenCalledWith({ userId: 11 }, { revoked: true });
  });

  it('refresh rotates token and returns a new token pair', async () => {
    const existing = {
      userId: 11,
      expiresAt: new Date(Date.now() + 60_000),
      revoked: false,
      user: { id: 11, username: 'carol', role: 'MISSION_OPERATOR' },
    };
    refreshTokenRepository.findOne.mockResolvedValue(existing);
    refreshTokenRepository.query.mockResolvedValue([]);

    const result = await service.refresh({ refreshToken: 'valid-token' });

    expect(existing.revoked).toBe(true);
    expect(refreshTokenRepository.save).toHaveBeenCalledWith(existing);
    expect(jwtService.signAsync).toHaveBeenCalledWith(
      expect.objectContaining({
        sub: 'carol',
        userId: 11,
        role: 'MISSION_OPERATOR',
        accountId: null,
        roles: ['MISSION_OPERATOR'],
      }),
    );
    expect(result.accessToken).toBe('signed-access-token');
    expect(typeof result.refreshToken).toBe('string');
  });

  it('me returns profile with account id when account exists', async () => {
    const createdAt = new Date('2026-09-24T12:00:00.000Z');
    usersService.findOneById.mockResolvedValue({
      id: 5,
      username: 'carol',
      role: 'CUSTOMER',
      createdAt,
    });
    refreshTokenRepository.query.mockResolvedValue([{ id: '90' }]);

    await expect(service.me(5)).resolves.toEqual({
      id: '5',
      username: 'carol',
      accountId: 90,
      roles: ['CUSTOMER'],
      createdOn: createdAt,
    });
  });

  it('me returns profile with null account id when account does not exist', async () => {
    usersService.findOneById.mockResolvedValue({
      id: 5,
      username: 'carol',
      role: 'CUSTOMER',
      createdAt: new Date('2026-09-24T12:00:00.000Z'),
    });
    refreshTokenRepository.query.mockResolvedValue([]);

    const result = await service.me(5);
    expect(result.accountId).toBeNull();
  });

  it('me rejects when user does not exist', async () => {
    usersService.findOneById.mockResolvedValue(null);

    await expect(service.me(999)).rejects.toThrow(UnauthorizedException);
  });

  it('hashes refresh token in refresh lookup query', async () => {
    refreshTokenRepository.findOne.mockResolvedValue(null);
    const rawToken = 'refresh-token-for-hash-check';
    const expectedHash = crypto.createHash('sha256').update(rawToken).digest('hex');

    await expect(service.refresh({ refreshToken: rawToken })).rejects.toThrow(UnauthorizedException);

    expect(refreshTokenRepository.findOne).toHaveBeenCalledWith({
      where: { tokenHash: expectedHash },
      relations: ['user'],
    });
  });
});

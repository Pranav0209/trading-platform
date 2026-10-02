jest.mock('@nestjs/common', () => ({
  Injectable: () => (target: unknown) => target,
  Logger: class Logger {
    warn = jest.fn();
  },
}));

jest.mock('@nestjs/typeorm', () => ({
  InjectRepository: () => () => undefined,
}));

jest.mock('../../src/auth/login-attempt.entity', () => ({
  LoginAttempt: class LoginAttempt {},
}));

jest.mock('typeorm', () => ({
  LessThan: (value: unknown) => ({ _type: 'LessThan', value }),
}));

const { LoginThrottleService } = require('../../src/auth/login-throttle.service') as {
  LoginThrottleService: any;
};

describe('LoginThrottleService', () => {
  let service: any;
  let repository: {
    count: jest.Mock;
    query: jest.Mock;
    create: jest.Mock;
    save: jest.Mock;
    delete: jest.Mock;
  };

  beforeEach(() => {
    repository = {
      count: jest.fn().mockResolvedValue(0),
      query: jest.fn().mockResolvedValue([{ count: 0 }]),
      create: jest.fn().mockImplementation((value) => value),
      save: jest.fn().mockResolvedValue(undefined),
      delete: jest.fn().mockResolvedValue(undefined),
    };

    service = new LoginThrottleService(repository as never);
  });

  it('returns false when failed attempt count is below threshold', async () => {
    repository.query.mockResolvedValue([{ count: 3 }]);

    await expect(service.isThrottled('alice', '10.0.0.1')).resolves.toBe(false);

    expect(repository.query).toHaveBeenCalled();
    expect(repository.query).toHaveBeenCalledWith(
      expect.stringContaining('SELECT COUNT(*) as count FROM auth.login_attempts'),
      ['alice', '10.0.0.1', expect.any(Date)],
    );
    expect(service.logger.warn).not.toHaveBeenCalled();
  });

  it('returns true and logs warning when failed attempt count reaches threshold', async () => {
    repository.query.mockResolvedValue([{ count: LoginThrottleService.MAX_ATTEMPTS }]);

    await expect(service.isThrottled('alice', '10.0.0.1')).resolves.toBe(true);

    expect(service.logger.warn).toHaveBeenCalledWith(
      `Login throttled for alice from 10.0.0.1: ${LoginThrottleService.MAX_ATTEMPTS} failed attempts`,
    );
  });

  it('treats missing count from raw query as not throttled', async () => {
    repository.query.mockResolvedValue([{}]);

    await expect(service.isThrottled('alice', '10.0.0.1')).resolves.toBe(false);
  });

  it('records failed attempt with success false', async () => {
    await service.recordFailedAttempt('alice', '10.0.0.1');

    expect(repository.create).toHaveBeenCalledWith({
      username: 'alice',
      ipAddress: '10.0.0.1',
      success: false,
    });
    expect(repository.save).toHaveBeenCalledWith({
      username: 'alice',
      ipAddress: '10.0.0.1',
      success: false,
    });
  });

  it('records successful attempt and deletes stale failed attempts', async () => {
    await service.recordSuccessfulAttempt('alice', '10.0.0.1');

    expect(repository.create).toHaveBeenCalledWith({
      username: 'alice',
      ipAddress: '10.0.0.1',
      success: true,
    });
    expect(repository.save).toHaveBeenCalledWith({
      username: 'alice',
      ipAddress: '10.0.0.1',
      success: true,
    });
    expect(repository.delete).toHaveBeenCalledWith(
      expect.objectContaining({
        username: 'alice',
        ipAddress: '10.0.0.1',
        success: false,
        createdAt: expect.any(Object),
      }),
    );
  });

  it('returns documented throttle configuration', () => {
    expect(LoginThrottleService.getConfig()).toEqual({
      maxAttempts: 5,
      cooldownSeconds: 900,
      cooldownMinutes: 15,
    });
  });
});

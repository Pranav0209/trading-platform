jest.mock('@nestjs/common', () => ({
  Controller: () => (target: unknown) => target,
  Post: () => () => undefined,
  Body: () => () => undefined,
  Get: () => () => undefined,
  UseGuards: () => () => undefined,
  Request: () => () => undefined,
  HttpCode: () => () => undefined,
}));

jest.mock('@nestjs/swagger', () => ({
  ApiTags: () => () => undefined,
  ApiOperation: () => () => undefined,
  ApiResponse: () => () => undefined,
  ApiBearerAuth: () => () => undefined,
  ApiProperty: () => () => undefined,
}));

jest.mock('../../src/auth/guards/jwt.guard', () => ({
  JwtAuthGuard: class JwtAuthGuard {},
}));

jest.mock('../../src/auth/auth.service', () => ({
  AuthService: class AuthService {},
}));

const { AuthController } = require('../../src/auth/auth.controller') as {
  AuthController: new (authService: unknown) => {
    register(registerDto: unknown): Promise<unknown>;
    login(loginDto: unknown, req: any): Promise<unknown>;
    refresh(refreshDto: unknown): Promise<unknown>;
    me(req: any): Promise<unknown>;
  };
};

describe('AuthController', () => {
  let controller: any;
  let authService: {
    register: jest.Mock;
    login: jest.Mock;
    refresh: jest.Mock;
    me: jest.Mock;
  };

  beforeEach(() => {
    authService = {
      register: jest.fn(),
      login: jest.fn(),
      refresh: jest.fn(),
      me: jest.fn(),
    };

    controller = new AuthController(authService as never);
  });

  it('should be defined', () => {
    expect(controller).toBeDefined();
  });

  it('register delegates to authService.register', async () => {
    const dto = { username: 'alice', email: 'alice@test.io', password: 'pass123' };
    const expected = { id: '1', username: 'alice' };
    authService.register.mockResolvedValue(expected);

    await expect(controller.register(dto)).resolves.toEqual(expected);
    expect(authService.register).toHaveBeenCalledWith(dto);
  });

  it('login uses first forwarded IP when x-forwarded-for header is present', async () => {
    const dto = { username: 'alice', password: 'pass123' };
    const req = {
      headers: { 'x-forwarded-for': ' 203.0.113.10 , 70.0.0.2 ' },
      ip: '10.10.10.10',
    };
    const expected = { accessToken: 'a', refreshToken: 'b' };
    authService.login.mockResolvedValue(expected);

    await expect(controller.login(dto, req)).resolves.toEqual(expected);
    expect(authService.login).toHaveBeenCalledWith(dto, '203.0.113.10');
  });

  it('login falls back to req.ip when x-forwarded-for is absent', async () => {
    const dto = { username: 'alice', password: 'pass123' };
    const req = {
      headers: {},
      ip: '10.10.10.10',
    };

    await controller.login(dto, req);
    expect(authService.login).toHaveBeenCalledWith(dto, '10.10.10.10');
  });

  it('login falls back to 0.0.0.0 when both forwarded header and req.ip are absent', async () => {
    const dto = { username: 'alice', password: 'pass123' };
    const req = {
      headers: {},
    };

    await controller.login(dto, req);
    expect(authService.login).toHaveBeenCalledWith(dto, '0.0.0.0');
  });

  it('refresh delegates to authService.refresh', async () => {
    const dto = { refreshToken: 'refresh-token' };
    const expected = { accessToken: 'new-token' };
    authService.refresh.mockResolvedValue(expected);

    await expect(controller.refresh(dto)).resolves.toEqual(expected);
    expect(authService.refresh).toHaveBeenCalledWith(dto);
  });

  it('me delegates to authService.me using req.user.userId', async () => {
    const req = {
      user: { userId: 77 },
    };
    const expected = { id: '77', username: 'alice' };
    authService.me.mockResolvedValue(expected);

    await expect(controller.me(req)).resolves.toEqual(expected);
    expect(authService.me).toHaveBeenCalledWith(77);
  });

  it('loads controller class when Reflect.metadata is unavailable', () => {
    const originalMetadata = (Reflect as any).metadata;

    jest.isolateModules(() => {
      (Reflect as any).metadata = undefined;
      const isolated = require('../../src/auth/auth.controller');
      expect(isolated.AuthController).toBeDefined();
    });

    (Reflect as any).metadata = originalMetadata;
  });
});



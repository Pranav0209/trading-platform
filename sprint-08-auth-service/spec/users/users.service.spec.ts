jest.mock('@nestjs/common', () => ({
  Injectable: () => (target: unknown) => target,
}));

jest.mock('@nestjs/typeorm', () => ({
  InjectRepository: () => () => undefined,
}));

const { UsersService } = require('../../src/users/users.service') as {
  UsersService: new (usersRepository: unknown) => {
    findOneByUsername(username: string): Promise<unknown>;
    findOneByEmail(email: string): Promise<unknown>;
    findOneById(id: number): Promise<unknown>;
    create(user: Record<string, unknown>): Promise<unknown>;
  };
};

describe('UsersService', () => {
  let service: any;
  let usersRepository: {
    findOneBy: jest.Mock;
    create: jest.Mock;
    save: jest.Mock;
  };

  beforeEach(() => {
    usersRepository = {
      findOneBy: jest.fn(),
      create: jest.fn(),
      save: jest.fn(),
    };

    service = new UsersService(usersRepository as never);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  it('findOneByUsername delegates to repository filter', async () => {
    const user = { id: 1, username: 'alice' };
    usersRepository.findOneBy.mockResolvedValue(user);

    await expect(service.findOneByUsername('alice')).resolves.toEqual(user);
    expect(usersRepository.findOneBy).toHaveBeenCalledWith({ username: 'alice' });
  });

  it('findOneByEmail delegates to repository filter', async () => {
    const user = { id: 1, email: 'alice@test.io' };
    usersRepository.findOneBy.mockResolvedValue(user);

    await expect(service.findOneByEmail('alice@test.io')).resolves.toEqual(user);
    expect(usersRepository.findOneBy).toHaveBeenCalledWith({ email: 'alice@test.io' });
  });

  it('findOneById delegates to repository filter', async () => {
    const user = { id: 99, username: 'alice' };
    usersRepository.findOneBy.mockResolvedValue(user);

    await expect(service.findOneById(99)).resolves.toEqual(user);
    expect(usersRepository.findOneBy).toHaveBeenCalledWith({ id: 99 });
  });

  it('create maps through repository create and save', async () => {
    const input = {
      username: 'alice',
      email: 'alice@test.io',
      role: 'CUSTOMER',
    };
    const created = { ...input, passwordHash: 'hash' };
    const saved = { id: 50, ...created };

    usersRepository.create.mockReturnValue(created);
    usersRepository.save.mockResolvedValue(saved);

    await expect(service.create(input)).resolves.toEqual(saved);
    expect(usersRepository.create).toHaveBeenCalledWith(input);
    expect(usersRepository.save).toHaveBeenCalledWith(created);
  });

  it('loads service class when Reflect.metadata is unavailable', () => {
    const originalMetadata = (Reflect as any).metadata;

    jest.isolateModules(() => {
      (Reflect as any).metadata = undefined;
      const isolated = require('../../src/users/users.service');
      expect(isolated.UsersService).toBeDefined();
    });

    (Reflect as any).metadata = originalMetadata;
  });
});
class UsersService {}

describe('UsersService', () => {
  let service: UsersService;

  beforeEach(() => {
    const mockRepository = {
      findOneBy: () => null,
      create: (value: unknown) => value,
      save: async (value: unknown) => value,
    };

    service = new UsersService(mockRepository as any);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });
});
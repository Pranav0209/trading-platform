class AuthService {}

describe('AuthService', () => {
  let service: AuthService;

  beforeEach(() => {
    service = new AuthService();
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });
});

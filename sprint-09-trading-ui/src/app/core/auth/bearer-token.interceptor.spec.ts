import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HttpClient, HTTP_INTERCEPTORS } from '@angular/common/http';
import { BearerTokenInterceptor } from './bearer-token.interceptor';
import { Session } from './session';

describe('BearerTokenInterceptor', () => {
  let httpMock: HttpTestingController;
  let httpClient: HttpClient;
  let session: Session;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        Session,
        {
          provide: HTTP_INTERCEPTORS,
          useClass: BearerTokenInterceptor,
          multi: true,
        },
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);
    httpClient = TestBed.inject(HttpClient);
    session = TestBed.inject(Session);
  });

  afterEach(() => {
    httpMock.verify();
    session.clearToken();
  });

  describe('attach bearer token case', () => {
    it('attaches bearer token to Trade API requests', () => {
      const token = 'test-bearer-token';
      session.setToken(token);

      httpClient.get('http://localhost:8081/api/v1/accounts/1').subscribe();

      const req = httpMock.expectOne('http://localhost:8081/api/v1/accounts/1');
      expect(req.request.headers.has('Authorization')).toBe(true);
      expect(req.request.headers.get('Authorization')).toBe(`Bearer ${token}`);
    });

    it('attaches bearer token to protected Auth API routes', () => {
      const token = 'test-bearer-token';
      session.setToken(token);

      httpClient.get('http://localhost:3001/auth/profile').subscribe();

      const req = httpMock.expectOne('http://localhost:3001/auth/profile');
      expect(req.request.headers.has('Authorization')).toBe(true);
      expect(req.request.headers.get('Authorization')).toBe(`Bearer ${token}`);
    });
  });

  describe('do-not-attach bearer token case', () => {
    it('does not attach token to unauthenticated login endpoint', () => {
      const token = 'test-bearer-token';
      session.setToken(token);

      httpClient.post('http://localhost:3001/auth/login', {}).subscribe();

      const req = httpMock.expectOne('http://localhost:3001/auth/login');
      expect(req.request.headers.has('Authorization')).toBe(false);
    });

    it('does not attach token to unauthenticated register endpoint', () => {
      const token = 'test-bearer-token';
      session.setToken(token);

      httpClient.post('http://localhost:3001/auth/register', {}).subscribe();

      const req = httpMock.expectOne('http://localhost:3001/auth/register');
      expect(req.request.headers.has('Authorization')).toBe(false);
    });

    it('does not attach token to unauthenticated refresh endpoint', () => {
      const token = 'test-bearer-token';
      session.setToken(token);

      httpClient.post('http://localhost:3001/auth/refresh', {}).subscribe();

      const req = httpMock.expectOne('http://localhost:3001/auth/refresh');
      expect(req.request.headers.has('Authorization')).toBe(false);
    });

    it('does not attach token to third-party origins', () => {
      const token = 'test-bearer-token';
      session.setToken(token);

      httpClient.get('http://example.com/api/data').subscribe();

      const req = httpMock.expectOne('http://example.com/api/data');
      expect(req.request.headers.has('Authorization')).toBe(false);
    });

    it('does not attach token when no session token exists', () => {
      session.clearToken();

      httpClient.get('http://localhost:8081/api/v1/accounts/1').subscribe();

      const req = httpMock.expectOne('http://localhost:8081/api/v1/accounts/1');
      expect(req.request.headers.has('Authorization')).toBe(false);
    });
  });
});

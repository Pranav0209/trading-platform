import { TestBed } from '@angular/core/testing';
import { Session } from './session';

describe('Session', () => {
  let service: Session;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(Session);
  });

  it('signing out clears the session', () => {
    service.setToken('test-token');
    expect(service.token()).toBe('test-token');
    expect(service.isLoggedIn).toBe(true);

    service.clearToken();
    expect(service.token()).toBe(null);
    expect(service.isLoggedIn).toBe(false);
  });
});

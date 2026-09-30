import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, UrlTree, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { SignIn } from './sign-in';
import { AuthService } from '../../../../generated/auth/api/auth.service';
import { Session } from '../session';
import { HttpErrorResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';

describe('SignIn', () => {
  let component: SignIn;
  let fixture: ComponentFixture<SignIn>;
  let mockAuthService: any;
  let mockSession: any;
  let router: Router;
  let navigateCalled = false;
  let navigateByUrlCalledWith: string | null = null;
  let returnTo: string | null = null;

  beforeEach(async () => {
    mockAuthService = {
      login: () => {}
    };
    mockSession = {
      setToken: () => {}
    };

    await TestBed.configureTestingModule({
      imports: [SignIn, ReactiveFormsModule],
      providers: [
        { provide: AuthService, useValue: mockAuthService },
        { provide: Session, useValue: mockSession },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: (key: string) => (key === 'returnTo' ? returnTo : null),
              },
            },
          },
        },
        provideRouter([])
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
    router.navigate = async () => {
      navigateCalled = true;
      return true;
    };
    router.navigateByUrl = async (url: string | UrlTree) => {
      navigateByUrlCalledWith = String(url);
      return true;
    };
    navigateCalled = false;
    navigateByUrlCalledWith = null;
    returnTo = null;

    fixture = TestBed.createComponent(SignIn);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('empty fields are blocked by validation', () => {
    const submitBtn = fixture.debugElement.query(By.css('[data-testid="submit-button"]')).nativeElement;
    expect(component.form.invalid).toBe(true);
    expect(submitBtn.disabled).toBe(true);
  });

  it('valid credentials sign in and redirect to dashboard', () => {
    let loginCalled = false;
    navigateCalled = false;
    
    // Create a valid JWT token with customer role (not admin)
    const validToken = [
      btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' })),
      btoa(JSON.stringify({ accountId: 1, roles: ['CUSTOMER'], sub: 'user123' })),
      'signature'
    ].join('.');
    
    mockAuthService.login = () => {
      loginCalled = true;
      return of({ accessToken: validToken });
    };
    
    component.form.setValue({ username: 'testuser', password: 'password123' });
    fixture.detectChanges();

    const submitBtn = fixture.debugElement.query(By.css('[data-testid="submit-button"]')).nativeElement;
    expect(submitBtn.disabled).toBe(false);

    component.onSubmit();

    expect(loginCalled).toBe(true);
    expect(navigateCalled).toBe(true);
  });

  it('refuses an off-origin return address and falls back to the default redirect', () => {
    returnTo = 'https://evil.example/phish';
    mockAuthService.login = () => of({ accessToken: 'test-token' });

    component.form.setValue({ username: 'testuser', password: 'password123' });
    fixture.detectChanges();

    component.onSubmit();

    expect(navigateByUrlCalledWith).toBeNull();
    expect(navigateCalled).toBe(true);
  });

  it('invalid credentials show a readable error', () => {
    mockAuthService.login = () => throwError(() => new HttpErrorResponse({ 
      status: 401,
      error: { errorCode: 'AUTH-401', message: 'Unauthorised' }
    }));
    
    component.form.setValue({ username: 'testuser', password: 'wrongpassword' });
    fixture.detectChanges();

    component.onSubmit();
    fixture.detectChanges();

    const errorMsg = fixture.debugElement.query(By.css('[data-testid="error-message"]'));
    expect(errorMsg.nativeElement.textContent).toContain('expired');
  });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { SignIn } from './sign-in';
import { AuthService } from '../../../../generated/auth/api/auth.service';
import { Session } from '../session';
import { By } from '@angular/platform-browser';

describe('SignIn', () => {
  let component: SignIn;
  let fixture: ComponentFixture<SignIn>;
  let mockAuthService: any;
  let mockSession: any;
  let router: Router;
  let navigateCalled = false;

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
        provideRouter([])
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
    router.navigate = async () => {
      navigateCalled = true;
      return true;
    };
    navigateCalled = false;

    fixture = TestBed.createComponent(SignIn);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('empty fields are blocked by validation', () => {
    const submitBtn = fixture.debugElement.query(By.css('[data-testid="submit-button"]')).nativeElement;
    expect(component.form.invalid).toBe(true);
    expect(submitBtn.disabled).toBe(true);
  });

  it('valid credentials sign in and redirect', () => {
    let loginCalled = false;
    navigateCalled = false;
    mockAuthService.login = () => {
      loginCalled = true;
      return of({ accessToken: 'test-token' });
    };
    
    component.form.setValue({ username: 'testuser', password: 'password123' });
    fixture.detectChanges();

    const submitBtn = fixture.debugElement.query(By.css('[data-testid="submit-button"]')).nativeElement;
    expect(submitBtn.disabled).toBe(false);

    component.onSubmit();

    expect(loginCalled).toBe(true);
    expect(navigateCalled).toBe(true);
  });

  it('invalid credentials show a readable error', () => {
    mockAuthService.login = () => throwError(() => new Error('Unauthorised'));
    
    component.form.setValue({ username: 'testuser', password: 'wrongpassword' });
    fixture.detectChanges();

    component.onSubmit();
    fixture.detectChanges();

    const errorMsg = fixture.debugElement.query(By.css('[data-testid="error-message"]'));
    expect(errorMsg.nativeElement.textContent).toContain('Sign in failed');
  });
});

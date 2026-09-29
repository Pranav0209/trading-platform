import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
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
  let mockRouter: any;

  beforeEach(async () => {
    mockAuthService = {
      login: () => {}
    };
    mockSession = {
      setToken: () => {}
    };
    mockRouter = {
      navigate: () => {}
    };

    await TestBed.configureTestingModule({
      imports: [SignIn, ReactiveFormsModule],
      providers: [
        { provide: AuthService, useValue: mockAuthService },
        { provide: Session, useValue: mockSession },
        { provide: Router, useValue: mockRouter },
      ]
    }).compileComponents();

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
    let navigateCalled = false;
    mockAuthService.login = () => {
      loginCalled = true;
      return of({ accessToken: 'test-token' });
    };
    mockRouter.navigate = () => { navigateCalled = true; };
    
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

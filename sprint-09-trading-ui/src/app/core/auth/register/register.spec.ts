import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Register } from './register';
import { AuthService } from '../../../../generated/auth/api/auth.service';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

describe('Register Component', () => {
  let component: Register;
  let fixture: ComponentFixture<Register>;
  let mockAuthService: any;
  let router: Router;
  let navigateCalled = false;

  beforeEach(async () => {
    mockAuthService = {
      register: () => {}
    };

    await TestBed.configureTestingModule({
      imports: [Register],
      providers: [
        { provide: AuthService, useValue: mockAuthService },
        provideRouter([])
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
    router.navigate = async () => {
      navigateCalled = true;
      return true;
    };
    navigateCalled = false;

    fixture = TestBed.createComponent(Register);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should not call auth service when form is invalid', () => {
    let registerCalled = false;
    mockAuthService.register = () => { registerCalled = true; };
    component.form.patchValue({ username: '', email: '', password: '' });
    component.onSubmit();
    expect(registerCalled).toBe(false);
  });

  it('should call auth service and redirect on success', () => {
    let registerCalled = false;
    mockAuthService.register = () => { registerCalled = true; return of({}); };
    component.form.patchValue({ username: 'testuser', email: 'test@example.com', password: 'password123' });
    
    component.onSubmit();
    
    expect(registerCalled).toBe(true);
  });

  it('should show error when username or email is taken', () => {
    mockAuthService.register = () => throwError(() => new HttpErrorResponse({ 
      status: 409,
      error: { errorCode: 'AUTH-409', message: 'Username already taken' }
    }));
    component.form.patchValue({ username: 'testuser', email: 'test@example.com', password: 'password123' });
    
    component.onSubmit();
    
    expect(component.errorMessage()).toBe('That username or email is already taken.');
  });
});

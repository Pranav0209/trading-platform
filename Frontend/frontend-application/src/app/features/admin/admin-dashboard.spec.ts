import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AdminDashboard } from './admin-dashboard';
import { AccountsService } from '../../../generated/trade/api/accounts.service';
import { of } from 'rxjs';

describe('AdminDashboard', () => {
  let component: AdminDashboard;
  let fixture: ComponentFixture<AdminDashboard>;
  let mockAccountsService: any;
  let getPendingUsersCalled = false;
  let createAccountCalled = false;

  beforeEach(async () => {
    mockAccountsService = {
      getPendingUsers: () => {
        getPendingUsersCalled = true;
        return of([]);
      },
      createAccount: () => {
        createAccountCalled = true;
        return of({});
      }
    };
    
    getPendingUsersCalled = false;
    createAccountCalled = false;

    await TestBed.configureTestingModule({
      imports: [AdminDashboard],
      providers: [
        { provide: AccountsService, useValue: mockAccountsService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AdminDashboard);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load pending users on init', () => {
    expect(getPendingUsersCalled).toBe(true);
  });
});

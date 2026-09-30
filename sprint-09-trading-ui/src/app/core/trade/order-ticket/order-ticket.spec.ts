import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { OrderTicket } from './order-ticket';
import { OrdersService } from '../../../../generated/trade/api/orders.service';
import { Session } from '../../auth/session';

describe('OrderTicket', () => {
  let component: OrderTicket;
  let fixture: ComponentFixture<OrderTicket>;
  let placeOrderCalled = false;
  let mockOrdersService: { placeOrder: () => any };

  const tokenWithAccount = [
    btoa(JSON.stringify({ alg: 'none', typ: 'JWT' })),
    btoa(JSON.stringify({ accountId: 42, roles: ['TRADER'] })),
    'sig'
  ].join('.');

  beforeEach(async () => {
    placeOrderCalled = false;
    mockOrdersService = {
      placeOrder: () => {
        placeOrderCalled = true;
        return of({});
      }
    };

    await TestBed.configureTestingModule({
      imports: [OrderTicket],
      providers: [
        { provide: OrdersService, useValue: mockOrdersService },
        { provide: Session, useValue: { token: () => tokenWithAccount } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(OrderTicket);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('a valid order submits and shows the returned status', () => {
    mockOrdersService.placeOrder = () => {
      placeOrderCalled = true;
      return of({
        orderId: 'ORD-123',
        status: 'NEW',
        message: 'Order accepted and queued',
        symbol: 'INFY.NS',
        side: 'BUY',
        quantity: 5,
        price: 1500.25
      });
    };

    component.form.patchValue({
      symbol: 'INFY.NS',
      side: 'BUY' as any,
      quantity: '5',
      price: '1500.25'
    });

    component.onSubmit();
    fixture.detectChanges();

    expect(placeOrderCalled).toBe(true);
    expect(component.responseMessage()).toContain('NEW');
    const msg = fixture.debugElement.query(By.css('[data-testid="response-message"]')).nativeElement.textContent;
    expect(msg).toContain('NEW');

    const accountInput = fixture.debugElement.query(By.css('[data-testid="account-input"]')).nativeElement;
    expect(accountInput.readOnly).toBe(true);
  });

  it('an invalid quantity is blocked before submission', () => {
    component.form.patchValue({
      symbol: 'INFY.NS',
      side: 'BUY' as any,
      quantity: '0',
      price: '125.40'
    });

    component.onSubmit();

    expect(placeOrderCalled).toBe(false);
  });

  it('a business-rule rejection shows the mapped message', () => {
    mockOrdersService.placeOrder = () => {
      placeOrderCalled = true;
      return throwError(() => new HttpErrorResponse({
        status: 400,
        error: { errorCode: 'ORD-400', message: 'Insufficient funds' }
      }));
    };

    component.form.patchValue({
      symbol: 'INFY.NS',
      side: 'BUY' as any,
      quantity: '10',
      price: '99999.00'
    });

    component.onSubmit();
    fixture.detectChanges();

    expect(component.responseType()).toBe('error');
    expect(component.responseMessage()).toBe('There is not enough cash for this order.');
  });
});

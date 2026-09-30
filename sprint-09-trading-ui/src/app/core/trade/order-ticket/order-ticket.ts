import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { OrdersService } from '../../../../generated/trade/api/orders.service';
import { OrderSide } from '../../../../generated/trade/model/orderSide';
import { PlaceOrderRequest } from '../../../../generated/trade/model/placeOrderRequest';
import { Session } from '../../auth/session';
import { ErrorCodeMapper } from '../../error/error-code-mapper';

const SYMBOL_PATTERN = /^(?:[A-Z][A-Z0-9]*(?:\.(?:NS|BO))?|FX:[A-Z]{6}|X:[A-Z0-9]+)$/;
const PRICE_PATTERN = /^\d+(?:\.\d{1,2})?$/;
const QUANTITY_PATTERN = /^[1-9]\d*$/;

@Component({
  selector: 'app-order-ticket',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './order-ticket.html',
  styleUrl: './order-ticket.css'
})
export class OrderTicket implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly ordersService = inject(OrdersService);
  private readonly session = inject(Session);
  private readonly errorCodeMapper = inject(ErrorCodeMapper);

  readonly orderSides = [OrderSide.Buy, OrderSide.Sell];
  readonly orderTypes = ['LIMIT', 'MARKET'];

  form = this.fb.nonNullable.group({
    symbol: ['', [Validators.required, Validators.pattern(SYMBOL_PATTERN)]],
    side: [OrderSide.Buy as OrderSide, [Validators.required]],
    orderType: ['LIMIT', [Validators.required]],
    quantity: ['', [Validators.required, Validators.pattern(QUANTITY_PATTERN)]],
    price: ['', [Validators.required, Validators.pattern(PRICE_PATTERN)]]
  });

  accountId = signal<number | null>(null);
  responseMessage = signal<string | null>(null);
  responseType = signal<'success' | 'error' | null>(null);
  submitting = signal(false);

  ngOnInit(): void {
    this.accountId.set(this.parseAccountId(this.session.token()));
    
    // Dynamically require or hide the price field based on order type
    this.form.controls.orderType.valueChanges.subscribe(type => {
      const priceControl = this.form.controls.price;
      if (type === 'MARKET') {
        priceControl.clearValidators();
        priceControl.disable();
        priceControl.setValue('');
      } else {
        priceControl.setValidators([Validators.required, Validators.pattern(PRICE_PATTERN)]);
        priceControl.enable();
      }
      priceControl.updateValueAndValidity();
    });
  }

  onSubmit(): void {
    if (this.form.invalid || (this.form.controls.orderType.value === 'LIMIT' && this.isPriceNonPositive())) {
      this.form.markAllAsTouched();
      return;
    }

    const accountId = this.accountId();
    if (accountId === null) {
      this.responseType.set('error');
      this.responseMessage.set('This session cannot place orders because no trade account is present in the token.');
      return;
    }

    this.submitting.set(true);
    this.responseType.set(null);
    this.responseMessage.set(null);

    const raw = this.form.getRawValue();
    const payload = {
      accountId,
      symbol: raw.symbol,
      side: raw.side,
      orderType: raw.orderType,
      quantity: Number(raw.quantity),
      idempotencyKey: this.createIdempotencyKey()
    } as any;

    if (raw.orderType === 'LIMIT') {
      payload.limitPrice = Number(raw.price);
    }

    this.ordersService.placeOrder(payload).subscribe({
      next: (res) => {
        this.submitting.set(false);
        this.responseType.set('success');
        this.responseMessage.set(`Order ${res.orderId} is ${res.status}. ${res.message}`);
      },
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        this.responseType.set('error');
        this.responseMessage.set(this.mapOrderError(error));
      }
    });
  }

  isPriceNonPositive(): boolean {
    if (this.form.controls.orderType.value === 'MARKET') {
      return false; // Price is irrelevant for MARKET orders
    }
    const priceControl = this.form.controls.price;
    const rawPrice = priceControl.value.trim();
    if (rawPrice === '') {
      return false;
    }

    const price = Number(rawPrice);
    return !Number.isFinite(price) || price <= 0;
  }

  private parseAccountId(token: string | null): number | null {
    if (!token) {
      return null;
    }

    try {
      const payloadBase64 = token.split('.')[1];
      if (!payloadBase64) {
        return null;
      }

      const json = atob(payloadBase64.replace(/-/g, '+').replace(/_/g, '/'));
      const payload = JSON.parse(json);
      const id = Number(payload.accountId);
      return Number.isInteger(id) && id > 0 ? id : null;
    } catch {
      return null;
    }
  }

  private createIdempotencyKey(): string {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }

    const random = Math.random().toString(16).slice(2);
    return `ui-${Date.now()}-${random}`;
  }

  private mapOrderError(error: HttpErrorResponse): string {
    return this.errorCodeMapper.mapErrorToMessage(error);
  }
}

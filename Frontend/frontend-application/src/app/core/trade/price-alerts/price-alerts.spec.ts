import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { PriceAlertsComponent } from './price-alerts';
import { PriceAlertsService } from '../../../../generated/trade/api/priceAlerts.service';
import { AlertResponse, GetAlerts200Response, CreateAlertRequestDirectionEnum } from '../../../../generated/trade/model/models';
import { DashboardService } from '../../../features/dashboard/dashboard.service';
import { Session } from '../../auth/session';
import { of, throwError } from 'rxjs';

describe('PriceAlertsComponent - Generated Service Integration Tests', () => {
  let component: PriceAlertsComponent;
  let fixture: ComponentFixture<PriceAlertsComponent>;
  let priceAlertsService: jasmine.SpyObj<PriceAlertsService>;
  let dashboardService: jasmine.SpyObj<DashboardService>;
  let session: jasmine.SpyObj<Session>;
  let activatedRoute: any;

  // Sample test data matching generated AlertResponse type
  const mockAlertResponse: AlertResponse = {
    id: 1,
    accountId: 123,
    symbol: 'AAPL',
    displayName: 'Apple Inc.',
    direction: 'ABOVE',
    threshold: 150.00,
    state: 'ACTIVE',
    lastFiredAt: null,
    deliveryStatus: 'PENDING',
    failureReason: null,
    createdAt: '2026-10-08T10:00:00Z'
  };

  const mockGetAlertsResponse: GetAlerts200Response = {
    alerts: [mockAlertResponse],
    count: 1,
    limit: 100
  };

  beforeEach(async () => {
    // Create spy objects with proper typing
    priceAlertsService = jasmine.createSpyObj<PriceAlertsService>('PriceAlertsService', [
      'getAlerts',
      'createAlert',
      'deleteAlert',
      'resetAlert',
      'getAlert'
    ]);

    dashboardService = jasmine.createSpyObj<DashboardService>('DashboardService', 
      ['accountCurrency'],
      { accountCurrency: 'USD' }
    );

    session = jasmine.createSpyObj<Session>('Session', ['accountId']);
    session.accountId.and.returnValue(123);

    activatedRoute = {
      queryParams: of({})
    };

    await TestBed.configureTestingModule({
      imports: [PriceAlertsComponent, FormsModule],
      providers: [
        { provide: PriceAlertsService, useValue: priceAlertsService },
        { provide: DashboardService, useValue: dashboardService },
        { provide: Session, useValue: session },
        { provide: ActivatedRoute, useValue: activatedRoute }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PriceAlertsComponent);
    component = fixture.componentInstance;
  });

  describe('Service Integration', () => {
    it('should use PriceAlertsService (not PriceAlertService)', () => {
      // This test validates the import is correct
      expect(component.alertService).toEqual(priceAlertsService);
    });

    it('should load alerts with proper GetAlerts200Response type', () => {
      priceAlertsService.getAlerts.and.returnValue(of(mockGetAlertsResponse));

      component.loadAlerts();

      expect(priceAlertsService.getAlerts).toHaveBeenCalledWith(123);
      expect(component.alerts.length).toBe(1);
      expect(component.alerts[0]).toEqual(mockAlertResponse);
      expect(component.alertLimit).toBe(100);
    });

    it('should handle getAlerts errors properly', () => {
      const errorResponse = { error: { message: 'Unauthorized' }, message: 'Unauthorized' };
      priceAlertsService.getAlerts.and.returnValue(throwError(() => errorResponse));

      component.loadAlerts();
      fixture.detectChanges();

      // Wait for setTimeout to execute
      jasmine.clock().install();
      jasmine.clock().tick(0);
      jasmine.clock().uninstall();

      expect(component.error).toContain('Failed to load alerts');
      expect(component.isLoadingAlerts).toBe(false);
    });
  });

  describe('Type Safety - Alert Array', () => {
    it('should have alerts array of type AlertResponse[]', () => {
      priceAlertsService.getAlerts.and.returnValue(of(mockGetAlertsResponse));

      component.loadAlerts();

      // Type is enforced at compile time, but we can verify the structure
      expect(component.alerts[0].id).toBe(1);
      expect(component.alerts[0].symbol).toBe('AAPL');
      expect(component.alerts[0].direction).toBe('ABOVE');
      expect(component.alerts[0].threshold).toBe(150.00);
      expect(component.alerts[0].state).toBe('ACTIVE');
      expect(component.alerts[0].deliveryStatus).toBe('PENDING');
    });

    it('should filter alerts by state with proper type checking', () => {
      component.alerts = [
        { ...mockAlertResponse, state: 'ACTIVE' },
        { ...mockAlertResponse, id: 2, state: 'TRIGGERED' }
      ];

      component.selectedState = 'ACTIVE';
      component.filterAlerts();

      expect(component.filteredAlerts.length).toBe(1);
      expect(component.filteredAlerts[0].state).toBe('ACTIVE');
    });
  });

  describe('Type Safety - Form Fields', () => {
    it('should have threshold as number type (not string)', () => {
      expect(typeof component.newAlertForm.threshold).toBe('number');
      expect(component.newAlertForm.threshold).toBe(0);
    });

    it('should have direction using CreateAlertRequestDirectionEnum', () => {
      expect(component.newAlertForm.direction).toBe(CreateAlertRequestDirectionEnum.Above);
    });

    it('should validate threshold as number', () => {
      component.newAlertForm.symbol = 'AAPL';
      component.newAlertForm.threshold = -1;

      component.createAlert();

      expect(component.error).toBe('Threshold must be a positive number');
    });

    it('should convert threshold number properly in createAlert', () => {
      priceAlertsService.createAlert.and.returnValue(of(mockAlertResponse));
      component.newAlertForm.symbol = 'AAPL';
      component.newAlertForm.threshold = 150;
      component.newAlertForm.direction = CreateAlertRequestDirectionEnum.Above;

      component.createAlert();

      expect(priceAlertsService.createAlert).toHaveBeenCalledWith(
        123,
        jasmine.objectContaining({
          symbol: 'AAPL',
          threshold: 150,
          direction: CreateAlertRequestDirectionEnum.Above
        })
      );
    });
  });

  describe('CreateAlert Method', () => {
    it('should create alert with proper CreateAlertRequest type', () => {
      priceAlertsService.createAlert.and.returnValue(of(mockAlertResponse));

      component.newAlertForm.symbol = 'MSFT';
      component.newAlertForm.threshold = 200;
      component.newAlertForm.direction = CreateAlertRequestDirectionEnum.Below;

      component.createAlert();

      expect(priceAlertsService.createAlert).toHaveBeenCalledWith(
        123,
        {
          symbol: 'MSFT',
          threshold: 200,
          direction: CreateAlertRequestDirectionEnum.Below
        }
      );
    });

    it('should add created alert to alerts array', () => {
      priceAlertsService.createAlert.and.returnValue(of(mockAlertResponse));

      component.alerts = [];
      component.newAlertForm.symbol = 'AAPL';
      component.newAlertForm.threshold = 150;

      component.createAlert();

      expect(component.alerts.length).toBe(1);
      expect(component.alerts[0]).toEqual(mockAlertResponse);
    });

    it('should reset form after successful creation', () => {
      priceAlertsService.createAlert.and.returnValue(of(mockAlertResponse));

      component.newAlertForm.symbol = 'AAPL';
      component.newAlertForm.threshold = 150;

      component.createAlert();

      expect(component.newAlertForm.symbol).toBe('');
      expect(component.newAlertForm.threshold).toBe(0);
      expect(component.newAlertForm.direction).toBe(CreateAlertRequestDirectionEnum.Above);
    });
  });

  describe('DeleteAlert Method', () => {
    it('should delete alert by id', () => {
      spyOn(window, 'confirm').and.returnValue(true);
      priceAlertsService.deleteAlert.and.returnValue(of(undefined));

      component.alerts = [mockAlertResponse];
      component.deleteAlert(mockAlertResponse);

      expect(priceAlertsService.deleteAlert).toHaveBeenCalledWith(123, 1);
      expect(component.alerts.length).toBe(0);
    });

    it('should handle delete confirmation', () => {
      spyOn(window, 'confirm').and.returnValue(false);
      priceAlertsService.deleteAlert.and.returnValue(of(undefined));

      component.alerts = [mockAlertResponse];
      component.deleteAlert(mockAlertResponse);

      expect(priceAlertsService.deleteAlert).not.toHaveBeenCalled();
      expect(component.alerts.length).toBe(1);
    });
  });

  describe('ResetAlert Method', () => {
    it('should reset triggered alert to active', () => {
      spyOn(window, 'confirm').and.returnValue(true);

      const resetAlert = { ...mockAlertResponse, state: 'TRIGGERED' } as AlertResponse;
      const resettedAlert = { ...resetAlert, state: 'ACTIVE' } as AlertResponse;

      priceAlertsService.resetAlert.and.returnValue(of(resettedAlert));

      component.alerts = [resetAlert];
      component.resetAlert(resetAlert);

      expect(priceAlertsService.resetAlert).toHaveBeenCalledWith(123, 1);
      expect(component.alerts[0].state).toBe('ACTIVE');
    });
  });

  describe('State Filtering', () => {
    it('should handle valid state types', () => {
      const validStates: Array<'ALL' | 'ACTIVE' | 'TRIGGERED' | 'DORMANT'> = ['ALL', 'ACTIVE', 'TRIGGERED', 'DORMANT'];

      validStates.forEach(state => {
        component.selectedState = state;
        expect(component.selectedState).toBe(state);
      });
    });

    it('should correctly filter by ACTIVE state', () => {
      component.alerts = [
        { ...mockAlertResponse, id: 1, state: 'ACTIVE' },
        { ...mockAlertResponse, id: 2, state: 'TRIGGERED' },
        { ...mockAlertResponse, id: 3, state: 'ACTIVE' }
      ];

      component.selectedState = 'ACTIVE';
      component.filterAlerts();

      expect(component.filteredAlerts.length).toBe(2);
      expect(component.filteredAlerts.every(a => a.state === 'ACTIVE')).toBe(true);
    });
  });

  describe('Badge Classes', () => {
    it('should return correct badge class for state', () => {
      expect(component.getStateBadgeClass('ACTIVE')).toContain('success');
      expect(component.getStateBadgeClass('TRIGGERED')).toContain('warning');
      expect(component.getStateBadgeClass('DORMANT')).toContain('secondary');
    });

    it('should return correct badge class for delivery status', () => {
      expect(component.getDeliveryBadgeClass('DELIVERED')).toContain('success');
      expect(component.getDeliveryBadgeClass('FAILED')).toContain('danger');
      expect(component.getDeliveryBadgeClass('PENDING')).toContain('info');
    });
  });

  describe('Helper Methods', () => {
    it('should format date correctly', () => {
      const dateStr = '2026-10-08T10:00:00Z';
      const formatted = component.formatDate(dateStr);

      expect(formatted).not.toBe('N/A');
      expect(formatted.length > 0).toBe(true);
    });

    it('should handle null dates', () => {
      expect(component.formatDate(null)).toBe('N/A');
    });

    it('should get direction label correctly', () => {
      expect(component.getDirectionLabel('ABOVE')).toContain('rises above');
      expect(component.getDirectionLabel('BELOW')).toContain('falls below');
    });

    it('should calculate alert count progress', () => {
      component.alerts = [
        { ...mockAlertResponse, state: 'ACTIVE' },
        { ...mockAlertResponse, id: 2, state: 'TRIGGERED' },
        { ...mockAlertResponse, id: 3, state: 'DORMANT' }
      ];
      component.alertLimit = 100;

      const progress = component.getAlertCountProgress();

      expect(progress).toBe(2); // 2 active/triggered out of 100
    });
  });

  describe('Error Handling', () => {
    it('should handle 409 conflict error when creating alert', () => {
      const error = { status: 409, error: { message: 'Limit exceeded' }, message: 'Conflict' };
      priceAlertsService.createAlert.and.returnValue(throwError(() => error));

      component.newAlertForm.symbol = 'AAPL';
      component.newAlertForm.threshold = 150;

      component.createAlert();
      fixture.detectChanges();

      jasmine.clock().install();
      jasmine.clock().tick(0);
      jasmine.clock().uninstall();

      expect(component.error).toContain('Alert limit exceeded');
    });

    it('should handle generic error when creating alert', () => {
      const error = { status: 400, error: { message: 'Bad request' }, message: 'Error' };
      priceAlertsService.createAlert.and.returnValue(throwError(() => error));

      component.newAlertForm.symbol = 'AAPL';
      component.newAlertForm.threshold = 150;

      component.createAlert();
      fixture.detectChanges();

      jasmine.clock().install();
      jasmine.clock().tick(0);
      jasmine.clock().uninstall();

      expect(component.error).toContain('Failed to create alert');
    });
  });
});

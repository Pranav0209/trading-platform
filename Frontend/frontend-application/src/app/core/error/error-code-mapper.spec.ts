import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ErrorCodeMapper } from './error-code-mapper';

describe('ErrorCodeMapper', () => {
  let service: ErrorCodeMapper;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ErrorCodeMapper);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('completeness', () => {
    it('every code in both catalogues maps to a message', () => {
      const completeness = service.getCompleteness();
      
      expect(completeness.complete).toBe(true);
      expect(completeness.unmappedCodes.length).toBe(0);
      expect(completeness.mappedCount).toBe(completeness.totalKnown);
    });

    it('maps all auth API error codes', () => {
      const mappings = service.getMappings();
      
      expect(mappings['AUTH-401']).toBeTruthy();
      expect(mappings['AUTH-409']).toBeTruthy();
    });

    it('maps all trade API error codes', () => {
      const mappings = service.getMappings();
      
      expect(mappings['ACC-404']).toBeTruthy();
      expect(mappings['ACC-403']).toBeTruthy();
      expect(mappings['INS-404']).toBeTruthy();
      expect(mappings['ORD-400']).toBeTruthy();
      expect(mappings['ORD-409']).toBeTruthy();
    });

    it('maps platform/validation error codes', () => {
      const mappings = service.getMappings();
      
      expect(mappings['VAL-422']).toBeTruthy();
    });
  });

  describe('known error codes', () => {
    it('AUTH-401 renders an authentication message', () => {
      const error = new HttpErrorResponse({
        status: 401,
        error: { errorCode: 'AUTH-401', message: 'Unauthorised' }
      });

      const message = service.mapErrorToMessage(error);
      
      expect(message).toContain('expired');
      expect(message).toContain('sign in');
    });

    it('AUTH-409 renders a registration conflict message', () => {
      const error = new HttpErrorResponse({
        status: 409,
        error: { errorCode: 'AUTH-409', message: 'Username already taken' }
      });

      const message = service.mapErrorToMessage(error);
      
      expect(message).toContain('already taken');
    });

    it('VAL-422 renders a validation message', () => {
      const error = new HttpErrorResponse({
        status: 422,
        error: { errorCode: 'VAL-422', message: 'Invalid input' }
      });

      const message = service.mapErrorToMessage(error);
      
      expect(message).toContain('not acceptable');
    });

    it('ACC-404 renders an account not found message', () => {
      const error = new HttpErrorResponse({
        status: 404,
        error: { errorCode: 'ACC-404', message: 'Account not found' }
      });

      const message = service.mapErrorToMessage(error);
      
      expect(message).toContain('account');
      expect(message.toLowerCase()).toContain('could not');
    });

    it('ACC-403 renders an account authorization message', () => {
      const error = new HttpErrorResponse({
        status: 403,
        error: { errorCode: 'ACC-403', message: 'Account not active' }
      });

      const message = service.mapErrorToMessage(error);
      
      expect(message).toContain('not active');
    });

    it('INS-404 renders an instrument not found message', () => {
      const error = new HttpErrorResponse({
        status: 404,
        error: { errorCode: 'INS-404', message: 'Instrument not found' }
      });

      const message = service.mapErrorToMessage(error);
      
      expect(message).toContain('instrument');
      expect(message).toContain('not be traded');
    });

    it('ORD-400 renders an insufficient funds message', () => {
      const error = new HttpErrorResponse({
        status: 400,
        error: { errorCode: 'ORD-400', message: 'Insufficient funds' }
      });

      const message = service.mapErrorToMessage(error);
      
      expect(message).toContain('cash');
    });

    it('ORD-409 renders an insufficient holdings or duplicate order message', () => {
      const error = new HttpErrorResponse({
        status: 409,
        error: { errorCode: 'ORD-409', message: 'Insufficient holdings' }
      });

      const message = service.mapErrorToMessage(error);
      
      expect(message).toContain('holding');
      expect(message).toContain('already placed');
    });
  });

  describe('unknown error codes', () => {
    it('an unrecognised code falls back to a readable sentence', () => {
      const error = new HttpErrorResponse({
        status: 500,
        error: { errorCode: 'UNKNOWN-999', message: 'Something went wrong' }
      });

      const message = service.mapErrorToMessage(error);
      
      // Should provide feedback from the message or generate a fallback
      expect(message).toBeTruthy();
      // When message is provided, it should return it as fallback
      expect(message).toBe('Something went wrong');
    });
  });

  describe('network errors', () => {
    it('a request that never reached a service (status 0) renders a readable sentence', () => {
      const error = new HttpErrorResponse({
        status: 0,
        statusText: '',
        error: null
      });

      const message = service.mapErrorToMessage(error);
      
      // Should be a helpful, actionable message
      expect(message).toBeTruthy();
      expect(message).not.toBe('');
      expect(message).toContain('Could not reach');
      expect(message).toContain('running');
    });

    it('status 0 takes precedence over error code in body', () => {
      const error = new HttpErrorResponse({
        status: 0,
        error: { errorCode: 'AUTH-401', message: 'Unauthorised' }
      });

      const message = service.mapErrorToMessage(error);
      
      expect(message).toContain('Could not reach');
      expect(message).not.toContain('expired');
    });
  });

  describe('missing error information', () => {
    it('handles missing error code gracefully', () => {
      const error = new HttpErrorResponse({
        status: 500,
        error: { message: 'Internal server error' }
      });

      const message = service.mapErrorToMessage(error);
      
      // Should provide the message from the error
      expect(message).toBeTruthy();
      expect(message).toBe('Internal server error');
    });

    it('handles null error body', () => {
      const error = new HttpErrorResponse({
        status: 500,
        error: null
      });

      const message = service.mapErrorToMessage(error);
      
      expect(message).toBeTruthy();
      expect(message).toContain('unexpected');
    });
  });
});

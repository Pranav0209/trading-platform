import { Injectable } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';

/**
 * Maps API error codes and HTTP status codes to trader-facing messages.
 * Branching happens on error codes, not messages or status codes alone.
 */
@Injectable({
  providedIn: 'root'
})
export class ErrorCodeMapper {
  /**
   * Complete mapping of all error codes from both auth and trade API contracts.
   * Organized by service to make it clear which codes belong where.
   */
  private readonly errorMessages: Record<string, string> = {
    // Auth API Errors
    'AUTH-401': 'Your session has expired. Please sign in again.',
    'AUTH-409': 'That username or email is already taken.',
    'AUTH-429': 'Too many login attempts. Your account is temporarily locked. Please try again in a few minutes.',
    
    // Platform/Validation Errors (used by both APIs)
    'VAL-422': 'One or more fields are not acceptable.',
    
    // Trade API Errors
    'ACC-404': 'The trading account could not be found.',
    'ACC-403': 'This account is not active, or this sign-in may not trade it.',
    'INS-404': 'This instrument cannot be traded.',
    'ORD-400': 'There is not enough cash for this order.',
    'ORD-409': 'There is not enough holding to sell, or this order was already placed.',
  };

  /**
   * All known error codes from both API contracts.
   * Used to verify completeness of the mapping.
   */
  private readonly allKnownCodes = new Set([
    'AUTH-401',
    'AUTH-409',
    'AUTH-429',
    'VAL-422',
    'ACC-404',
    'ACC-403',
    'INS-404',
    'ORD-400',
    'ORD-409',
  ]);

  /**
   * Maps an HTTP error response to a trader-facing message.
   * Handles status 0 (network/CORS errors) and unknown error codes.
   *
   * @param error - The HTTP error response
   * @param context - 'login' for login page, undefined for other API calls
   * @returns A trader-friendly error message
   */
  mapErrorToMessage(error: HttpErrorResponse, context?: 'login'): string {
    // Handle network errors: status 0 means no response from service
    if (error.status === 0) {
      return 'Could not reach the service. Check that it is running and allows this origin.';
    }

    // Extract error code from response body
    const code = (error.error?.errorCode || error.error?.error) as string | undefined;
    const message = error.error?.message as string | undefined;

    // Special handling for AUTH-401 in login context
    if (context === 'login' && code === 'AUTH-401') {
      return 'Invalid username or password.';
    }

    // If we have a known code, return its mapped message
    if (code && this.errorMessages[code]) {
      return this.errorMessages[code];
    }

    // Fallback to auth-service message if provided
    if (message) {
      return message;
    }

    // Fallback for unknown codes
    if (code) {
      return `An error occurred: ${code}. Please try again.`;
    }

    // Fallback when no error code is present
    return 'An unexpected error occurred. Please try again.';
  }

  /**
   * Gets all mapped error codes and their messages.
   * Useful for testing completeness.
   */
  getMappings(): Record<string, string> {
    return { ...this.errorMessages };
  }

  /**
   * Checks if all known error codes have mappings.
   * Returns an object with completeness info.
   */
  getCompleteness(): {
    complete: boolean;
    mappedCount: number;
    totalKnown: number;
    unmappedCodes: string[];
  } {
    const mappedCodes = new Set(Object.keys(this.errorMessages));
    const unmappedCodes = Array.from(this.allKnownCodes).filter(
      (code) => !mappedCodes.has(code)
    );

    return {
      complete: unmappedCodes.length === 0,
      mappedCount: mappedCodes.size,
      totalKnown: this.allKnownCodes.size,
      unmappedCodes,
    };
  }
}

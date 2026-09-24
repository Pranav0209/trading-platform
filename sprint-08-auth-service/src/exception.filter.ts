import {
  ExceptionFilter,
  Catch,
  ArgumentsHost,
  HttpException,
  BadRequestException,
  ConflictException,
  UnauthorizedException,
  Logger,
} from '@nestjs/common';
import { Request, Response } from 'express';

@Catch()
export class GlobalExceptionFilter implements ExceptionFilter {
  private readonly logger = new Logger('GlobalExceptionFilter');

  catch(exception: unknown, host: ArgumentsHost) {
    const ctx = host.switchToHttp();
    const response = ctx.getResponse<Response>();
    const request = ctx.getRequest<Request>();

    let status = 500;
    let errorCode = 'ERR-500';
    let message = 'Internal Server Error';

    if (exception instanceof BadRequestException) {
      status = 422;
      errorCode = 'VAL-422';
      message = 'Invalid input';
    } else if (exception instanceof ConflictException) {
      const exResponse = exception.getResponse() as any;
      status = 409;
      errorCode = exResponse?.errorCode || 'AUTH-409';
      message = exResponse?.message || 'Conflict';
    } else if (exception instanceof UnauthorizedException) {
      const exResponse = exception.getResponse() as any;
      status = 401;
      errorCode = exResponse?.errorCode || 'AUTH-401';
      message = exResponse?.message || 'Unauthorised';
    } else if (exception instanceof HttpException) {
      status = exception.getStatus();
      const exResponse = exception.getResponse() as any;
      if (typeof exResponse === 'object' && 'errorCode' in exResponse) {
        errorCode = exResponse.errorCode;
        message = exResponse.message;
      } else if (typeof exResponse === 'object' && 'message' in exResponse) {
        message = exResponse.message;
      }
    }

    // Log without sensitive data
    const logMessage = `${request.method} ${request.path} - ${status} ${errorCode}`;
    if (status >= 500) {
      this.logger.error(logMessage, exception instanceof Error ? exception.stack : '');
    } else {
      this.logger.warn(logMessage);
    }

    response.status(status).json({
      errorCode,
      message,
    });
  }
}

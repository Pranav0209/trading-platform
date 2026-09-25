import { ExceptionFilter, Catch, ArgumentsHost, HttpException } from '@nestjs/common';
import { Response } from 'express';

@Catch()
export class GlobalExceptionFilter implements ExceptionFilter {
  catch(exception: any, host: ArgumentsHost) {
    const ctx = host.switchToHttp();
    const response = ctx.getResponse<Response>();

    if (exception instanceof HttpException) {
      const status = exception.getStatus();
      const res = exception.getResponse() as any;

      if (status === 422) {
        return response.status(422).json({
          errorCode: 'VAL-422',
          message: 'Invalid input',
        });
      }

      if (res && res.errorCode) {
        return response.status(status).json({
          errorCode: res.errorCode,
          message: res.message,
        });
      }
      
      // Fallback for other HTTP Exceptions not wrapped with errorCode
      return response.status(status).json({
        errorCode: `ERR-${status}`,
        message: res.message || 'Error',
      });
    }

    console.error(exception);
    response.status(500).json({
      errorCode: 'INTERNAL_ERROR',
      message: 'Internal server error',
    });
  }
}

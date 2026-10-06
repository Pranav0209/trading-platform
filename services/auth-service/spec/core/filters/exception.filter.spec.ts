jest.mock('@nestjs/common', () => {
  class HttpException extends Error {
    private readonly status: number;
    private readonly response: unknown;

    constructor(response: unknown, status: number) {
      super(typeof response === 'string' ? response : 'HttpException');
      this.name = 'HttpException';
      this.response = response;
      this.status = status;
    }

    getStatus() {
      return this.status;
    }

    getResponse() {
      return this.response;
    }
  }

  class BadRequestException extends HttpException {
    constructor(message: string) {
      super({ message }, 400);
      this.name = 'BadRequestException';
    }
  }

  return {
    Catch: () => (target: unknown) => target,
    HttpException,
    BadRequestException,
  };
});

const { GlobalExceptionFilter } = require('../../../src/core/filters/exception.filter') as {
  GlobalExceptionFilter: new () => {
    catch(exception: unknown, host: unknown): void;
  };
};

const { BadRequestException, HttpException } = require('@nestjs/common') as {
  BadRequestException: new (message: string) => Error;
  HttpException: new (response: unknown, status: number) => Error;
};

describe('GlobalExceptionFilter', () => {
  let filter: any;

  beforeEach(() => {
    filter = new GlobalExceptionFilter();
  });

  const createHost = (response: { status: jest.Mock; json: jest.Mock }) =>
    ({
      switchToHttp: () => ({
        getResponse: () => response,
      }),
    });

  const createResponse = () => {
    const response = {
      status: jest.fn(),
      json: jest.fn(),
    };
    response.status.mockReturnValue(response);
    return response;
  };

  it('maps status 422 to VAL-422 and Invalid input', () => {
    const response = createResponse();
    const host = createHost(response);
    const exception = new HttpException({ message: 'validation failed' }, 422);

    filter.catch(exception, host);

    expect(response.status).toHaveBeenCalledWith(422);
    expect(response.json).toHaveBeenCalledWith({
      errorCode: 'VAL-422',
      message: 'Invalid input',
    });
  });

  it('returns provided errorCode and message for wrapped HttpException', () => {
    const response = createResponse();
    const host = createHost(response);
    const exception = new HttpException(
      { errorCode: 'AUTH-401', message: 'Unauthorised' },
      401,
    );

    filter.catch(exception, host);

    expect(response.status).toHaveBeenCalledWith(401);
    expect(response.json).toHaveBeenCalledWith({
      errorCode: 'AUTH-401',
      message: 'Unauthorised',
    });
  });

  it('falls back to ERR-status and response message for unwrapped HttpException', () => {
    const response = createResponse();
    const host = createHost(response);
    const exception = new BadRequestException('Bad payload');

    filter.catch(exception, host);

    expect(response.status).toHaveBeenCalledWith(400);
    expect(response.json).toHaveBeenCalledWith({
      errorCode: 'ERR-400',
      message: 'Bad payload',
    });
  });

  it('falls back to generic Error message when HttpException response has no message', () => {
    const response = createResponse();
    const host = createHost(response);
    const exception = new HttpException({}, 418);

    filter.catch(exception, host);

    expect(response.status).toHaveBeenCalledWith(418);
    expect(response.json).toHaveBeenCalledWith({
      errorCode: 'ERR-418',
      message: 'Error',
    });
  });

  it('returns INTERNAL_ERROR and logs for non-HttpException errors', () => {
    const response = createResponse();
    const host = createHost(response);
    const exception = new Error('boom');
    const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => undefined);

    filter.catch(exception, host);

    expect(errorSpy).toHaveBeenCalledWith(exception);
    expect(response.status).toHaveBeenCalledWith(500);
    expect(response.json).toHaveBeenCalledWith({
      errorCode: 'INTERNAL_ERROR',
      message: 'Internal server error',
    });
  });
});

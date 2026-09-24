import {
  Injectable,
  CanActivate,
  ExecutionContext,
  UnauthorizedException,
} from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { ConfigService } from '@nestjs/config';

@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(
    private jwtService: JwtService,
    private configService: ConfigService,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const request = context.switchToHttp().getRequest();
    const authHeader = request.headers.authorization;

    if (!authHeader) {
      throw new UnauthorizedException({
        errorCode: 'AUTH-401',
        message: 'Unauthorised',
      });
    }

    const [scheme, token] = authHeader.split(' ');

    if (scheme !== 'Bearer' || !token) {
      throw new UnauthorizedException({
        errorCode: 'AUTH-401',
        message: 'Unauthorised',
      });
    }

    try {
      const secret = this.configService.get<string>('JWT_SECRET');
      const expectedIssuer = this.configService.get<string>('JWT_ISSUER');
      const payload = this.jwtService.verify(token, {
        secret,
        algorithms: ['HS256'],
      });

      const hasValidClaims =
        typeof payload.sub === 'string' &&
        Number.isInteger(payload.accountId) &&
        Array.isArray(payload.roles) &&
        payload.roles.length > 0 &&
        payload.roles.every((role: unknown) => typeof role === 'string') &&
        typeof payload.iss === 'string' &&
        typeof payload.iat === 'number' &&
        typeof payload.exp === 'number';

      if (!hasValidClaims) {
        throw new UnauthorizedException({
          errorCode: 'AUTH-401',
          message: 'Unauthorised',
        });
      }

      // Validate issuer only when the environment pins one.
      if (expectedIssuer && payload.iss !== expectedIssuer) {
        throw new UnauthorizedException({
          errorCode: 'AUTH-401',
          message: 'Unauthorised',
        });
      }

      request.user = payload;
      return true;
    } catch (error) {
      throw new UnauthorizedException({
        errorCode: 'AUTH-401',
        message: 'Unauthorised',
      });
    }
  }
}

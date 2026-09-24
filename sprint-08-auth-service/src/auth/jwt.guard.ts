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
      const payload = this.jwtService.verify(token, { secret });

      // Validate required claims
      if (!payload.sub || !payload.accountId || !payload.iss || !payload.roles) {
        throw new UnauthorizedException({
          errorCode: 'AUTH-401',
          message: 'Unauthorised',
        });
      }

      // Validate issuer
      if (payload.iss !== 'auth-service') {
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

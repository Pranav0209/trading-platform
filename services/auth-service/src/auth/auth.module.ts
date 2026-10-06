import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { JwtModule } from '@nestjs/jwt';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { AuthService } from './auth.service';
import { AuthController } from './auth.controller';
import { UsersModule } from '../users/users.module';
import { RefreshToken } from '../refresh-tokens/refresh-token.entity';
import { LoginAttempt } from './login-attempt.entity';
import { LoginThrottleService } from './login-throttle.service';

@Module({
  imports: [
    UsersModule,
    TypeOrmModule.forFeature([RefreshToken, LoginAttempt]),
    JwtModule.registerAsync({
      imports: [ConfigModule],
      useFactory: async (configService: ConfigService) => ({
        secret: configService.get<string>('JWT_SECRET'),
        signOptions: { expiresIn: (configService.get<string>('JWT_TOKEN_EXPIRY') ?? '15m') as any },
      }),
      inject: [ConfigService],
    }),
  ],
  providers: [AuthService, LoginThrottleService],
  controllers: [AuthController],
})
export class AuthModule {}

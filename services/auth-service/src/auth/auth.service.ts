import { Injectable, UnauthorizedException, ConflictException } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { ConfigService } from '@nestjs/config';
import { UsersService } from '../users/users.service';
import { LoginThrottleService } from './login-throttle.service';
import { RegisterDto } from './dto/register.dto';
import { LoginDto } from './dto/login.dto';
import { RefreshDto } from './dto/refresh.dto';
import * as bcrypt from 'bcryptjs';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { RefreshToken } from '../refresh-tokens/refresh-token.entity';
import * as crypto from 'crypto';
import { EmailJSService } from '../core/emailjs.service';

@Injectable()
export class AuthService {
  constructor(
    private usersService: UsersService,
    private jwtService: JwtService,
    private loginThrottleService: LoginThrottleService,
    private configService: ConfigService,
    @InjectRepository(RefreshToken)
    private refreshTokenRepository: Repository<RefreshToken>,
    private emailJSService: EmailJSService,
  ) {}

  private generateOtp(): string {
    return crypto.randomInt(100000, 1000000).toString();
  }

  async register(registerDto: RegisterDto) {
    const existingUser = await this.usersService.findOneByUsername(registerDto.username);
    const existingEmail = await this.usersService.findOneByEmail(registerDto.email);
    
    // If user exists and is already ACTIVE, fail the registration
    if ((existingUser && existingUser.status === 'ACTIVE') || (existingEmail && existingEmail.status === 'ACTIVE')) {
      throw new ConflictException({ errorCode: 'AUTH-409', message: 'Registration failed' });
    }

    const salt = await bcrypt.genSalt(12);
    const passwordHash = await bcrypt.hash(registerDto.password, salt);

    const otpCode = this.generateOtp();
    const otpExpiresAt = new Date(Date.now() + 2 * 60 * 1000); // 2 minutes
    const hashedOtp = await bcrypt.hash(otpCode, 10);

    let targetUser = existingUser || existingEmail;
    
    if (targetUser) {
      // User is PENDING, we can just update their record to allow re-registration
      const updatedUser = await this.usersService.update(targetUser.id, {
        username: registerDto.username,
        email: registerDto.email,
        passwordHash,
        otpCode: hashedOtp,
        otpExpiresAt,
      });
      if (!updatedUser) {
        throw new ConflictException({ errorCode: 'AUTH-500', message: 'Failed to update pending user' });
      }
      targetUser = updatedUser;
    } else {
      targetUser = await this.usersService.create({
        username: registerDto.username,
        email: registerDto.email,
        passwordHash,
        role: 'CUSTOMER',
        status: 'PENDING',
        notificationChannel: 'EMAIL',
        otpCode: hashedOtp,
        otpExpiresAt,
      });
    }

    await this.emailJSService.sendOtpEmail(targetUser.email, otpCode);

    return {
      message: 'Registration successful. Please check your email for the OTP to activate your account.',
      id: targetUser.id.toString(),
      username: targetUser.username,
      email: targetUser.email,
    };
  }

  async verifyOtp(email: string, otpCode: string) {
    const user = await this.usersService.findOneByEmail(email);
    if (!user) {
      throw new UnauthorizedException({ errorCode: 'AUTH-401', message: 'User not found or invalid OTP' });
    }

    if (user.status !== 'PENDING') {
      throw new ConflictException({ errorCode: 'AUTH-409', message: 'User is already active' });
    }

    if (!user.otpCode || !user.otpExpiresAt || user.otpExpiresAt < new Date()) {
      throw new UnauthorizedException({ errorCode: 'AUTH-401', message: 'Invalid or expired OTP' });
    }

    const isOtpValid = await bcrypt.compare(otpCode, user.otpCode);
    if (!isOtpValid) {
      throw new UnauthorizedException({ errorCode: 'AUTH-401', message: 'Invalid or expired OTP' });
    }

    await this.usersService.update(user.id, {
      status: 'ACTIVE',
      otpCode: null,
      otpExpiresAt: null,
    });

    return { message: 'Account activated successfully' };
  }

  async resendOtp(email: string) {
    const user = await this.usersService.findOneByEmail(email);
    if (!user) {
      throw new UnauthorizedException({ errorCode: 'AUTH-401', message: 'User not found' });
    }

    if (user.status !== 'PENDING') {
      throw new ConflictException({ errorCode: 'AUTH-409', message: 'User is already active' });
    }

    const otpCode = this.generateOtp();
    const otpExpiresAt = new Date(Date.now() + 2 * 60 * 1000); // 2 minutes
    const hashedOtp = await bcrypt.hash(otpCode, 10);

    await this.usersService.update(user.id, {
      otpCode: hashedOtp,
      otpExpiresAt,
    });

    await this.emailJSService.sendOtpEmail(user.email, otpCode);
    return { message: 'OTP resent successfully. Please check your email.' };
  }

  async requestEmailUpdate(userId: number, newEmail: string) {
    const existingEmail = await this.usersService.findOneByEmail(newEmail);
    if (existingEmail) {
      throw new ConflictException({ errorCode: 'AUTH-409', message: 'Email is already in use' });
    }

    const otpCode = this.generateOtp();
    const otpExpiresAt = new Date(Date.now() + 2 * 60 * 1000); // 2 minutes
    const hashedOtp = await bcrypt.hash(otpCode, 10);

    await this.usersService.update(userId, {
      otpCode: hashedOtp,
      otpExpiresAt,
      pendingNewEmail: newEmail,
    });

    await this.emailJSService.sendOtpEmail(newEmail, otpCode);
    return { message: 'OTP sent to new email address' };
  }

  async verifyEmailUpdate(userId: number, newEmail: string, otpCode: string) {
    const user = await this.usersService.findOneById(userId);
    if (!user) {
      throw new UnauthorizedException({ errorCode: 'AUTH-401', message: 'User not found' });
    }

    if (user.pendingNewEmail !== newEmail) {
      throw new UnauthorizedException({ errorCode: 'AUTH-401', message: 'Invalid or expired OTP' });
    }

    if (!user.otpCode || !user.otpExpiresAt || user.otpExpiresAt < new Date()) {
      throw new UnauthorizedException({ errorCode: 'AUTH-401', message: 'Invalid or expired OTP' });
    }

    const isOtpValid = await bcrypt.compare(otpCode, user.otpCode);
    if (!isOtpValid) {
      throw new UnauthorizedException({ errorCode: 'AUTH-401', message: 'Invalid or expired OTP' });
    }

    await this.usersService.update(userId, {
      email: newEmail,
      otpCode: null,
      otpExpiresAt: null,
      pendingNewEmail: null,
    });

    return { message: 'Email updated successfully' };
  }

  async login(loginDto: LoginDto, ipAddress: string = '0.0.0.0') {
    // Check if this user+IP combination is currently throttled
    const isThrottled = await this.loginThrottleService.isThrottled(
      loginDto.username,
      ipAddress,
    );

    // Always attempt to find the user and verify password
    // This ensures both unknown user and wrong password take comparable time
    const user = await this.usersService.findOneByUsername(loginDto.username);
    let isPasswordMatch = false;

    if (user) {
      isPasswordMatch = await bcrypt.compare(loginDto.password, user.passwordHash);
    } else {
      // Dummy compare to prevent timing attacks
      await bcrypt.compare(loginDto.password, '$2a$12$dummyhashdummyhashdummyhashdummyhashdummyhashdummyhash').catch(() => {
        // Ignore errors during dummy comparison
      });
    }

    // Determine if login should succeed (user must be ACTIVE)
    const loginSuccessful = !isThrottled && user && isPasswordMatch && user.status === 'ACTIVE';

    if (!loginSuccessful) {
      // If throttled, return specific error code so frontend knows to show retry message
      if (isThrottled) {
        throw new UnauthorizedException({ 
          errorCode: 'AUTH-429', 
          message: 'Too many login attempts. Please try again in a few minutes.' 
        });
      }

      // For other failures (wrong password, user not found, inactive), record attempt
      await this.loginThrottleService.recordFailedAttempt(
        loginDto.username,
        ipAddress,
      );

      // Return generic error (doesn't leak if user exists)
      throw new UnauthorizedException({ errorCode: 'AUTH-401', message: 'Unauthorised' });
    }

    // Record successful login
    await this.loginThrottleService.recordSuccessfulAttempt(
      loginDto.username,
      ipAddress,
    );

    return this.generateTokens(user);
  }

  async refresh(refreshDto: RefreshDto) {
    const hashedPresentedToken = crypto.createHash('sha256').update(refreshDto.refreshToken).digest('hex');
    const storedToken = await this.refreshTokenRepository.findOne({
      where: { tokenHash: hashedPresentedToken },
      relations: ['user'],
    });

    if (!storedToken || storedToken.expiresAt < new Date()) {
      throw new UnauthorizedException({ errorCode: 'AUTH-401', message: 'Unauthorised' });
    }

    if (storedToken.revoked) {
      // Token theft detected! Revoke all tokens for this user.
      await this.refreshTokenRepository.update({ userId: storedToken.userId }, { revoked: true });
      throw new UnauthorizedException({ errorCode: 'AUTH-401', message: 'Unauthorised' });
    }

    // Revoke the presented token
    storedToken.revoked = true;
    await this.refreshTokenRepository.save(storedToken);

    return this.generateTokens(storedToken.user);
  }

  async logout(refreshToken: string) {
    if (!refreshToken) return;
    const hashedPresentedToken = crypto.createHash('sha256').update(refreshToken).digest('hex');
    const storedToken = await this.refreshTokenRepository.findOne({
      where: { tokenHash: hashedPresentedToken },
    });

    if (storedToken && !storedToken.revoked) {
      storedToken.revoked = true;
      await this.refreshTokenRepository.save(storedToken);
    }
  }

  private async generateTokens(user: any) {
    // Attempt to get accountId if they have an account
    const accounts = await this.refreshTokenRepository.query('SELECT id FROM accounts WHERE user_id = $1 LIMIT 1', [user.id]);
    const accountId = accounts && accounts.length > 0 ? parseInt(accounts[0].id, 10) : null;

    const payload = {
      sub: user.username, // Sprint 6 expects username in sub
      userId: Number(user.id), // Sprint 6 expects userId as Number
      accountId: accountId, // Sprint 8 requirement
      roles: [user.role], // Sprint 8 requirement
      iss: 'auth-service'
    };

    const accessToken = await this.jwtService.signAsync(payload);
    
    // Generate new refresh token
    const rawRefreshToken = crypto.randomBytes(32).toString('hex');
    const hashedRefreshToken = crypto.createHash('sha256').update(rawRefreshToken).digest('hex');

    const refreshTokenExpiryDays = this.configService.get<number>('JWT_REFRESH_TOKEN_EXPIRY_DAYS') || 7;
    const refreshTokenEntity = this.refreshTokenRepository.create({
      userId: user.id,
      tokenHash: hashedRefreshToken,
      expiresAt: new Date(Date.now() + refreshTokenExpiryDays * 24 * 60 * 60 * 1000),
      revoked: false,
    });
    await this.refreshTokenRepository.save(refreshTokenEntity);

    const accessTokenExpiresInSeconds = this.configService.get<number>('ACCESS_TOKEN_EXPIRES_IN_SECONDS') || 900;
    return {
      accessToken,
      refreshToken: rawRefreshToken,
      tokenType: 'Bearer',
      expiresIn: accessTokenExpiresInSeconds,
    };
  }

  async me(userId: number) {
    const user = await this.usersService.findOneById(userId);
    if (!user) {
      throw new UnauthorizedException({ errorCode: 'AUTH-401', message: 'Unauthorised' });
    }
    const accounts = await this.refreshTokenRepository.query('SELECT id FROM accounts WHERE user_id = $1 LIMIT 1', [user.id]);
    const accountId = accounts && accounts.length > 0 ? parseInt(accounts[0].id, 10) : null;

    return {
      id: user.id.toString(),
      username: user.username,
      accountId: accountId,
      roles: [user.role],
      createdOn: user.createdAt,
    };
  }
}

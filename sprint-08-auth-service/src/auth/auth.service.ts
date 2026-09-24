import { Injectable, UnauthorizedException, ConflictException } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { UsersService } from '../users/users.service';
import { LoginThrottleService } from './login-throttle.service';
import { RegisterDto } from './dto/register.dto';
import { LoginDto } from './dto/login.dto';
import { RefreshDto } from './dto/refresh.dto';
import * as bcrypt from 'bcryptjs';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { RefreshToken } from './refresh-token.entity';
import * as crypto from 'crypto';

@Injectable()
export class AuthService {
  constructor(
    private usersService: UsersService,
    private jwtService: JwtService,
    private loginThrottleService: LoginThrottleService,
    @InjectRepository(RefreshToken)
    private refreshTokenRepository: Repository<RefreshToken>,
  ) {}

  async register(registerDto: RegisterDto) {
    const existingUser = await this.usersService.findOneByUsername(registerDto.username);
    const existingEmail = await this.usersService.findOneByEmail(registerDto.email);
    if (existingUser || existingEmail) {
      throw new ConflictException({ errorCode: 'AUTH-409', message: 'Registration failed' });
    }

    const salt = await bcrypt.genSalt(12);
    const passwordHash = await bcrypt.hash(registerDto.password, salt);

    const newUser = await this.usersService.create({
      username: registerDto.username,
      email: registerDto.email,
      passwordHash,
      role: 'CUSTOMER',
      status: 'ACTIVE',
    });

    return {
      id: newUser.id.toString(),
      username: newUser.username,
      email: newUser.email,
      roles: [newUser.role],
      createdOn: newUser.createdAt,
    };
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

    // Determine if login should succeed
    const loginSuccessful = !isThrottled && user && isPasswordMatch;

    if (!loginSuccessful) {
      // Record failed attempt
      await this.loginThrottleService.recordFailedAttempt(
        loginDto.username,
        ipAddress,
      );

      // Return identical response regardless of failure reason
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

  private async generateTokens(user: any) {
    // Attempt to get accountId if they have an account
    const accounts = await this.refreshTokenRepository.query('SELECT id FROM accounts WHERE user_id = $1 LIMIT 1', [user.id]);
    const accountId = accounts && accounts.length > 0 ? parseInt(accounts[0].id, 10) : null;

    const payload = {
      sub: user.username, // Sprint 6 expects username in sub
      userId: Number(user.id), // Sprint 6 expects userId as Number
      role: user.role, // Sprint 6 expects role as String
      accountId: accountId, // Sprint 8 requirement
      roles: [user.role], // Sprint 8 requirement
      iss: 'auth-service'
    };

    const accessToken = await this.jwtService.signAsync(payload);
    
    // Generate new refresh token
    const rawRefreshToken = crypto.randomBytes(32).toString('hex');
    const hashedRefreshToken = crypto.createHash('sha256').update(rawRefreshToken).digest('hex');

    const refreshTokenEntity = this.refreshTokenRepository.create({
      userId: user.id,
      tokenHash: hashedRefreshToken,
      expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000), // 7 days
      revoked: false,
    });
    await this.refreshTokenRepository.save(refreshTokenEntity);

    return {
      accessToken,
      refreshToken: rawRefreshToken,
      tokenType: 'Bearer',
      expiresIn: 900,
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

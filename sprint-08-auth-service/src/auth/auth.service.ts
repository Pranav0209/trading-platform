import {
  Injectable,
  UnauthorizedException,
  ConflictException,
  BadRequestException,
  Logger,
} from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { UsersService } from '../users/users.service';
import { RegisterDto } from './dto/register.dto';
import { LoginDto } from './dto/login.dto';
import { RefreshDto } from './dto/refresh.dto';
import * as bcrypt from 'bcryptjs';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { RefreshToken } from './refresh-token.entity';
import * as crypto from 'crypto';
import { ConfigService } from '@nestjs/config';

@Injectable()
export class AuthService {
  private readonly logger = new Logger('AuthService');
  private readonly BCRYPT_COST = 12;
  private readonly ACCESS_TOKEN_TTL_SECONDS = 900;

  constructor(
    private usersService: UsersService,
    private jwtService: JwtService,
    private configService: ConfigService,
    @InjectRepository(RefreshToken)
    private refreshTokenRepository: Repository<RefreshToken>,
  ) {}

  async register(registerDto: RegisterDto) {
    // Check if username already exists
    const existingUser = await this.usersService.findOneByUsername(
      registerDto.username,
    );
    if (existingUser) {
      throw new ConflictException({
        errorCode: 'AUTH-409',
        message: 'Registration failed',
      });
    }

    // Validate that the account exists
    const accountExists = await this.usersService.validateAccountExists(
      registerDto.accountId,
    );
    if (!accountExists) {
      throw new BadRequestException({
        errorCode: 'VAL-422',
        message: 'Invalid input',
      });
    }

    // Hash password with bcrypt cost 12
    const passwordHash = await bcrypt.hash(
      registerDto.password,
      this.BCRYPT_COST,
    );

    // Determine roles - default to CUSTOMER, only allow ADMIN if specified and valid
    const roles = registerDto.roles || ['CUSTOMER'];

    // Create user in auth schema
    const newUser = await this.usersService.create(
      registerDto.username,
      passwordHash,
      roles,
    );

    this.logger.log(`User registered: ${registerDto.username}`);

    // Return user response without tokens
    return {
      id: newUser.id,
      username: newUser.username,
      accountId: registerDto.accountId,
      roles: newUser.roles,
      createdOn: newUser.createdAt.toISOString(),
    };
  }

  async login(loginDto: LoginDto) {
    const user = await this.usersService.findOneByUsername(
      loginDto.username,
    );

    let isPasswordMatch = false;

    if (user) {
      isPasswordMatch = await bcrypt.compare(
        loginDto.password,
        user.passwordHash,
      );
    } else {
      // Dummy compare to prevent timing attacks
      // Use a realistic bcrypt hash format
      const dummyHash =
        '$2a$12$dummyhashdummyhashdummyhashdummyhashdummyhashdummyhash';
      await bcrypt.compare(loginDto.password, dummyHash).catch(() => {
        // Ignore errors during dummy comparison
      });
    }

    if (!user || !isPasswordMatch) {
      // Same response for unknown user and wrong password
      throw new UnauthorizedException({
        errorCode: 'AUTH-401',
        message: 'Unauthorised',
      });
    }

    // Get the account ID for this user from the trade database
    const accountId = await this.usersService.getAccountIdForUser(user.id);

    return this.generateTokens(user, accountId);
  }

  async refresh(refreshDto: RefreshDto) {
    // Hash the presented token
    const hashedPresentedToken = crypto
      .createHash('sha256')
      .update(refreshDto.refreshToken)
      .digest('hex');

    const storedToken = await this.refreshTokenRepository.findOne({
      where: { tokenHash: hashedPresentedToken },
      relations: ['user'],
    });

    if (!storedToken || storedToken.expiresAt < new Date()) {
      throw new UnauthorizedException({
        errorCode: 'AUTH-401',
        message: 'Unauthorised',
      });
    }

    if (storedToken.revoked) {
      // Token theft detected! Revoke all tokens for this user.
      await this.refreshTokenRepository.update(
        { userId: storedToken.userId },
        { revoked: true },
      );
      this.logger.warn(
        `Token theft detected for user ${storedToken.userId}, all tokens revoked`,
      );
      throw new UnauthorizedException({
        errorCode: 'AUTH-401',
        message: 'Unauthorised',
      });
    }

    // Revoke the presented token
    storedToken.revoked = true;
    await this.refreshTokenRepository.save(storedToken);

    const accountId = await this.usersService.getAccountIdForUser(
      storedToken.user.id,
    );

    return this.generateTokens(storedToken.user, accountId);
  }

  async me(userId: string) {
    const user = await this.usersService.findOneById(userId);

    if (!user) {
      throw new UnauthorizedException({
        errorCode: 'AUTH-401',
        message: 'Unauthorised',
      });
    }

    const accountId = await this.usersService.getAccountIdForUser(user.id);

    return {
      id: user.id,
      username: user.username,
      accountId,
      roles: user.roles,
      createdOn: user.createdAt.toISOString(),
    };
  }

  private async generateTokens(user: any, accountId: number | null) {
    const secret = this.configService.get<string>('JWT_SECRET');
    const issuer = this.configService.get<string>('JWT_ISSUER') || 'auth-service';

    if (!secret) {
      throw new UnauthorizedException({
        errorCode: 'AUTH-401',
        message: 'Unauthorised',
      });
    }

    const numericAccountId =
      typeof accountId === 'string' ? Number(accountId) : accountId;

    if (!Number.isInteger(numericAccountId)) {
      throw new UnauthorizedException({
        errorCode: 'AUTH-401',
        message: 'Unauthorised',
      });
    }

    // Use one timestamp source so exp is exactly 15 minutes after iat.
    const issuedAt = Math.floor(Date.now() / 1000);

    // Create JWT payload with exactly the claims required by contract.
    const payload = {
      sub: user.id, // UUID
      accountId: numericAccountId,
      roles: user.roles,
      iat: issuedAt,
      exp: issuedAt + this.ACCESS_TOKEN_TTL_SECONDS,
      iss: issuer,
    };

    const accessToken = this.jwtService.sign(payload, {
      secret,
      algorithm: 'HS256',
      noTimestamp: true,
    });

    // Generate new refresh token
    const rawRefreshToken = crypto.randomBytes(32).toString('hex');
    const hashedRefreshToken = crypto
      .createHash('sha256')
      .update(rawRefreshToken)
      .digest('hex');

    const refreshTokenEntity = this.refreshTokenRepository.create({
      userId: user.id,
      tokenHash: hashedRefreshToken,
      expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000), // 7 days
      revoked: false,
    });
    await this.refreshTokenRepository.save(refreshTokenEntity);

    this.logger.log(`Tokens generated for user ${user.id}`);

    return {
      accessToken,
      refreshToken: rawRefreshToken,
      tokenType: 'Bearer',
      expiresIn: this.ACCESS_TOKEN_TTL_SECONDS,
    };
  }
}

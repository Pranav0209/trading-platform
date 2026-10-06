import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, LessThan } from 'typeorm';
import { ConfigService } from '@nestjs/config';
import { LoginAttempt } from './login-attempt.entity';

@Injectable()
export class LoginThrottleService {
  private readonly logger = new Logger('LoginThrottleService');
  private readonly maxAttempts: number;
  private readonly cooldownSeconds: number;

  constructor(
    @InjectRepository(LoginAttempt)
    private loginAttemptRepository: Repository<LoginAttempt>,
    private configService: ConfigService,
  ) {
    this.maxAttempts = this.configService.get<number>('LOGIN_MAX_ATTEMPTS') || 5;
    this.cooldownSeconds = this.configService.get<number>('LOGIN_COOLDOWN_SECONDS') || 900;
  }

  /**
   * Check if a username+IP combination is currently throttled
   * Returns true if too many failed attempts within cooldown window
   */
  async isThrottled(username: string, ipAddress: string): Promise<boolean> {
    const cooldownStart = new Date(
      Date.now() - this.cooldownSeconds * 1000,
    );

    // Count only recent failures (within cooldown window)
    const recentFailureCount = await this.loginAttemptRepository.query(
      `SELECT COUNT(*) as count FROM auth.login_attempts 
       WHERE username = $1 AND "ipAddress" = $2 AND success = false 
       AND created_at > $3`,
      [username, ipAddress, cooldownStart],
    );

    const failureCount = recentFailureCount[0]?.count || 0;
    const isThrottled = failureCount >= this.maxAttempts;

    if (isThrottled) {
      this.logger.warn(
        `Login throttled for ${username} from ${ipAddress}: ${failureCount} failed attempts`,
      );
    }

    return isThrottled;
  }

  /**
   * Record a failed login attempt
   */
  async recordFailedAttempt(username: string, ipAddress: string): Promise<void> {
    const attempt = this.loginAttemptRepository.create({
      username,
      ipAddress,
      success: false,
    });

    await this.loginAttemptRepository.save(attempt);
  }

  /**
   * Record a successful login attempt
   */
  async recordSuccessfulAttempt(
    username: string,
    ipAddress: string,
  ): Promise<void> {
    const attempt = this.loginAttemptRepository.create({
      username,
      ipAddress,
      success: true,
    });

    await this.loginAttemptRepository.save(attempt);

    // Clean up old failed attempts (older than 24 hours)
    const oneDayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000);
    await this.loginAttemptRepository.delete({
      username,
      ipAddress,
      success: false,
      createdAt: LessThan(oneDayAgo),
    });
  }

  /**
   * Get throttle configuration for documentation
   */
  getConfig() {
    return {
      maxAttempts: this.maxAttempts,
      cooldownSeconds: this.cooldownSeconds,
      cooldownMinutes: this.cooldownSeconds / 60,
    };
  }
}

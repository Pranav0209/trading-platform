import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, LessThan } from 'typeorm';
import { LoginAttempt } from './login-attempt.entity';

@Injectable()
export class LoginThrottleService {
  private readonly logger = new Logger('LoginThrottleService');

  // Configuration constants
  static readonly MAX_ATTEMPTS = 5;
  static readonly COOLDOWN_SECONDS = 900; // 15 minutes

  constructor(
    @InjectRepository(LoginAttempt)
    private loginAttemptRepository: Repository<LoginAttempt>,
  ) {}

  /**
   * Check if a username+IP combination is currently throttled
   * Returns true if too many failed attempts within cooldown window
   */
  async isThrottled(username: string, ipAddress: string): Promise<boolean> {
    const cooldownStart = new Date(
      Date.now() - LoginThrottleService.COOLDOWN_SECONDS * 1000,
    );

    // Count only recent failures (within cooldown window)
    const recentFailureCount = await this.loginAttemptRepository.query(
      `SELECT COUNT(*) as count FROM auth.login_attempts 
       WHERE username = $1 AND "ipAddress" = $2 AND success = false 
       AND created_at > $3`,
      [username, ipAddress, cooldownStart],
    );

    const failureCount = recentFailureCount[0]?.count || 0;
    const isThrottled = failureCount >= LoginThrottleService.MAX_ATTEMPTS;

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
  static getConfig() {
    return {
      maxAttempts: this.MAX_ATTEMPTS,
      cooldownSeconds: this.COOLDOWN_SECONDS,
      cooldownMinutes: this.COOLDOWN_SECONDS / 60,
    };
  }
}

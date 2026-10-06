import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
const emailjs = require('@emailjs/nodejs');

@Injectable()
export class EmailJSService {
  private readonly logger = new Logger(EmailJSService.name);

  constructor(private configService: ConfigService) {}

  async sendOtpEmail(toEmail: string, otpCode: string): Promise<boolean> {
    const serviceId = this.configService.get<string>('EMAILJS_SERVICE_ID');
    const templateId = this.configService.get<string>('EMAILJS_TEMPLATE_ID');
    const publicKey = this.configService.get<string>('EMAILJS_PUBLIC_KEY');
    const privateKey = this.configService.get<string>('EMAILJS_PRIVATE_KEY');

    if (!serviceId || !templateId || !publicKey || !privateKey) {
      this.logger.warn('EmailJS credentials are not fully configured in .env. Falling back to logging OTP.');
      this.logger.log(`[MOCK EMAIL] To: ${toEmail}, OTP: ${otpCode}`);
      return true; // Pretend it succeeded
    }

    try {
      await emailjs.send(
        serviceId,
        templateId,
        {
          to_email: toEmail,
          otp_code: otpCode,
          message: `Your verification code is: ${otpCode}. It will expire in 10 minutes.`
        },
        {
          publicKey: publicKey,
          privateKey: privateKey,
        }
      );
      this.logger.log(`OTP Email sent successfully to ${toEmail}`);
      return true;
    } catch (error) {
      this.logger.error(`Failed to send OTP email to ${toEmail}`, error);
      return false;
    }
  }
}

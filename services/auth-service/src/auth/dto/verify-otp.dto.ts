import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, IsString, Length } from 'class-validator';

export class VerifyOtpDto {
  @ApiProperty({ description: 'The email of the user' })
  @IsNotEmpty()
  @IsString()
  email: string;

  @ApiProperty({ description: 'The 6-digit OTP code' })
  @IsNotEmpty()
  @IsString()
  @Length(6, 6)
  otpCode: string;
}

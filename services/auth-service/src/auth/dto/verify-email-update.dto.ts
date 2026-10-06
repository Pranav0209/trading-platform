import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, IsString, Length, IsEmail } from 'class-validator';

export class VerifyEmailUpdateDto {
  @ApiProperty({ description: 'The new email address' })
  @IsNotEmpty()
  @IsEmail()
  newEmail: string;

  @ApiProperty({ description: 'The 6-digit OTP code sent to the new email' })
  @IsNotEmpty()
  @IsString()
  @Length(6, 6)
  otpCode: string;
}

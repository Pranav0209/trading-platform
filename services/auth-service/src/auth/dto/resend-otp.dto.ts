import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, IsString, IsEmail } from 'class-validator';

export class ResendOtpDto {
  @ApiProperty({ description: 'The email of the user' })
  @IsNotEmpty()
  @IsString()
  @IsEmail()
  email: string;
}

import { IsEmail, IsNotEmpty, Matches, MaxLength, MinLength } from 'class-validator';
import { ApiProperty } from '@nestjs/swagger';

const STRONG_PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)\S+$/;

export class RegisterDto {
  @ApiProperty({
    example: 'priya.menon',
    description: 'The username',
    minLength: 3,
    maxLength: 50,
    pattern: '^[a-zA-Z0-9._-]+$',
  })
  @IsNotEmpty()
  @MinLength(3)
  @MaxLength(50)
  @Matches(/^[a-zA-Z0-9._-]+$/)
  username: string;

  @ApiProperty({ example: 'priya.menon@example.com', description: 'The email address' })
  @IsNotEmpty()
  @IsEmail()
  @MaxLength(255)
  email: string;

  @ApiProperty({
    example: 'Password123',
    description: 'The password, minimum 8 characters, with uppercase, lowercase, and number, and no spaces',
    minLength: 8,
    maxLength: 128,
    pattern: '^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)\\S+$',
  })
  @IsNotEmpty()
  @MinLength(8)
  @MaxLength(128)
  @Matches(STRONG_PASSWORD_PATTERN)
  password: string;
}

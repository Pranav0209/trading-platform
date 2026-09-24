import { IsEmail, IsNotEmpty, MaxLength, MinLength } from 'class-validator';
import { ApiProperty } from '@nestjs/swagger';

export class RegisterDto {
  @ApiProperty({ example: 'priya.menon', description: 'The username' })
  @IsNotEmpty()
  @MaxLength(50)
  username: string;

  @ApiProperty({ example: 'priya.menon@example.com', description: 'The email address' })
  @IsNotEmpty()
  @IsEmail()
  @MaxLength(255)
  email: string;

  @ApiProperty({ example: 'correct horse battery staple', description: 'The password, minimum 8 characters', minLength: 8 })
  @IsNotEmpty()
  @MinLength(8)
  @MaxLength(100)
  password: string;
}

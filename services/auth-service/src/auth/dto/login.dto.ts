import { IsNotEmpty, MaxLength } from 'class-validator';
import { ApiProperty } from '@nestjs/swagger';

export class LoginDto {
  @ApiProperty({ example: 'priya.menon', description: 'The username' })
  @IsNotEmpty()
  @MaxLength(64)
  username: string;

  @ApiProperty({ example: 'correct horse battery staple', description: 'The password' })
  @IsNotEmpty()
  @MaxLength(128)
  password: string;
}

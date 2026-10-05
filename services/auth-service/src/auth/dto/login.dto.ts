import { MinLength, IsNotEmpty, Matches, MaxLength } from 'class-validator';
import { ApiProperty } from '@nestjs/swagger';

export class LoginDto {
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

  @ApiProperty({
    example: 'correct horse battery staple',
    description: 'The password',
    maxLength: 128,
  })
  @IsNotEmpty()
  @MaxLength(128)
  password: string;
}

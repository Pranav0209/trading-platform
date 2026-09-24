import { IsNotEmpty, IsString, MaxLength } from 'class-validator';

export class LoginDto {
  @IsNotEmpty()
  @IsString()
  @MaxLength(64)
  username: string;

  @IsNotEmpty()
  @IsString()
  @MaxLength(128)
  password: string;
}

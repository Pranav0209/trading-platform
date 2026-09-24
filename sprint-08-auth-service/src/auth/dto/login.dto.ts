import { IsNotEmpty, MaxLength } from 'class-validator';

export class LoginDto {
  @IsNotEmpty()
  @MaxLength(64)
  username: string;

  @IsNotEmpty()
  @MaxLength(128)
  password: string;
}

import {
  IsNotEmpty,
  IsString,
  MinLength,
  MaxLength,
  Matches,
  IsInt,
  Min,
  IsOptional,
  IsArray,
  ArrayMinSize,
  IsEnum,
} from 'class-validator';
import { Type } from 'class-transformer';

export enum Role {
  CUSTOMER = 'CUSTOMER',
  ADMIN = 'ADMIN',
}

export class RegisterDto {
  @IsNotEmpty()
  @IsString()
  @MinLength(3)
  @MaxLength(64)
  @Matches(/^[a-zA-Z0-9._-]+$/, {
    message: 'username must only contain alphanumeric characters, dots, underscores, and hyphens',
  })
  username: string;

  @IsNotEmpty()
  @IsString()
  @MinLength(12)
  @MaxLength(128)
  password: string;

  @IsNotEmpty()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  accountId: number;

  @IsOptional()
  @IsArray()
  @ArrayMinSize(1)
  @IsEnum(Role, { each: true })
  roles?: Role[];
}

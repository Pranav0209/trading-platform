import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, IsString, IsEmail } from 'class-validator';

export class RequestEmailUpdateDto {
  @ApiProperty({ description: 'The new email address' })
  @IsNotEmpty()
  @IsEmail()
  newEmail: string;
}

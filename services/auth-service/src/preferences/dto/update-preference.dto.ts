import { IsString, IsIn } from 'class-validator';
import { ApiProperty } from '@nestjs/swagger';

export class UpdatePreferenceDto {
  @ApiProperty({ example: 'EMAIL', enum: ['EMAIL', 'PUSH'], description: 'The desired notification channel' })
  @IsString()
  @IsIn(['EMAIL', 'PUSH'])
  notificationChannel: string;
}

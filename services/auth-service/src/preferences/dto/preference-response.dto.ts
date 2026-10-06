import { ApiProperty } from '@nestjs/swagger';

export class PreferenceResponseDto {
  @ApiProperty({ example: 'EMAIL', description: 'The user notification channel preference' })
  notificationChannel: string;
}

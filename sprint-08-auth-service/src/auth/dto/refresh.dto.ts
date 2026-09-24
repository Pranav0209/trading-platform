import { IsNotEmpty } from 'class-validator';
import { ApiProperty } from '@nestjs/swagger';

export class RefreshDto {
  @ApiProperty({ example: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f', description: 'The refresh token' })
  @IsNotEmpty()
  refreshToken: string;
}

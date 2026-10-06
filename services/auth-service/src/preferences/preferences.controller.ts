import { Controller, Get, Put, Body, UseGuards, Req, NotFoundException } from '@nestjs/common';
import { ApiTags, ApiBearerAuth, ApiOperation, ApiResponse } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt.guard';
import { UsersService } from '../users/users.service';
import { UpdatePreferenceDto } from './dto/update-preference.dto';
import { PreferenceResponseDto } from './dto/preference-response.dto';

@ApiTags('preferences')
@ApiBearerAuth()
@Controller('preferences')
@UseGuards(JwtAuthGuard)
export class PreferencesController {
  constructor(private readonly usersService: UsersService) {}

  @Get()
  @ApiOperation({ summary: 'Get current user preferences' })
  @ApiResponse({ status: 200, description: 'User preferences', type: PreferenceResponseDto })
  async getPreferences(@Req() req: any): Promise<PreferenceResponseDto> {
    const user = await this.usersService.findOneById(req.user.userId);
    if (!user) throw new NotFoundException('User not found');
    return { notificationChannel: user.notificationChannel };
  }

  @Put()
  @ApiOperation({ summary: 'Update current user preferences' })
  @ApiResponse({ status: 200, description: 'Updated user preferences', type: PreferenceResponseDto })
  async updatePreferences(@Req() req: any, @Body() updateDto: UpdatePreferenceDto): Promise<PreferenceResponseDto> {
    const updatedUser = await this.usersService.update(req.user.userId, {
      notificationChannel: updateDto.notificationChannel,
    });
    if (!updatedUser) throw new NotFoundException('User not found');
    return { notificationChannel: updatedUser.notificationChannel };
  }
}

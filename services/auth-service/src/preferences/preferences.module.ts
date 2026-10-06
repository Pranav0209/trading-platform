import { Module } from '@nestjs/common';
import { PreferencesController } from './preferences.controller';
import { UsersModule } from '../users/users.module';
import { AuthModule } from '../auth/auth.module';

@Module({
  imports: [UsersModule, AuthModule],
  controllers: [PreferencesController],
})
export class PreferencesModule {}

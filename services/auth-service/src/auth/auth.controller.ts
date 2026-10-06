import { Controller, Post, Body, Get, UseGuards, Request, HttpCode, Res, UnauthorizedException } from '@nestjs/common';
import { ApiTags, ApiOperation, ApiResponse, ApiBearerAuth } from '@nestjs/swagger';
import { AuthService } from './auth.service';
import { RegisterDto } from './dto/register.dto';
import { LoginDto } from './dto/login.dto';
import { RefreshDto } from './dto/refresh.dto';
import { VerifyOtpDto } from './dto/verify-otp.dto';
import { RequestEmailUpdateDto } from './dto/request-email-update.dto';
import { VerifyEmailUpdateDto } from './dto/verify-email-update.dto';
import { JwtAuthGuard } from './guards/jwt.guard';

@ApiTags('Auth')
@Controller('auth')
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  @ApiOperation({ summary: 'Register a new user' })
  @ApiResponse({ status: 201, description: 'User successfully created.' })
  @ApiResponse({ status: 409, description: 'Registration failed (Conflict).' })
  @Post('register')
  async register(@Body() registerDto: RegisterDto) {
    return this.authService.register(registerDto);
  }

  @ApiOperation({ summary: 'Login and get tokens' })
  @ApiResponse({ status: 200, description: 'Login successful.' })
  @ApiResponse({ status: 401, description: 'Unauthorised.' })
  @HttpCode(200)
  @Post('login')
  async login(@Body() loginDto: LoginDto, @Request() req: any, @Res({ passthrough: true }) res: any) {
    const ipAddress =
      req.headers['x-forwarded-for']?.split(',')[0].trim() || req.ip || '0.0.0.0';
    const result = await this.authService.login(loginDto, ipAddress);

    // Securely set the refresh token as an HttpOnly cookie
    res.cookie('refreshToken', result.refreshToken, {
      httpOnly: true,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'lax',
      path: '/',
    });

    // Remove the refresh token from the JSON body
    const { refreshToken, ...responseBody } = result;
    return responseBody;
  }

  @ApiOperation({ summary: 'Refresh access token using refresh token' })
  @ApiResponse({ status: 200, description: 'Tokens refreshed successfully.' })
  @ApiResponse({ status: 401, description: 'Unauthorised.' })
  @HttpCode(200)
  @Post('refresh')
  async refresh(@Request() req: any, @Res({ passthrough: true }) res: any) {
    const refreshToken = req.cookies['refreshToken'];
    if (!refreshToken) {
      throw new UnauthorizedException({ errorCode: 'AUTH-401', message: 'No refresh token cookie' });
    }

    const result = await this.authService.refresh({ refreshToken });
    
    // Set the newly rotated refresh token
    res.cookie('refreshToken', result.refreshToken, {
      httpOnly: true,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'lax',
      path: '/',
    });

    const { refreshToken: _, ...responseBody } = result;
    return responseBody;
  }

  @ApiOperation({ summary: 'Logout and clear refresh token' })
  @ApiResponse({ status: 200, description: 'Logged out successfully.' })
  @HttpCode(200)
  @Post('logout')
  async logout(@Request() req: any, @Res({ passthrough: true }) res: any) {
    const refreshToken = req.cookies['refreshToken'];
    
    if (refreshToken) {
      await this.authService.logout(refreshToken);
    }

    res.clearCookie('refreshToken', {
      httpOnly: true,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'lax',
      path: '/',
    });

    return { message: 'Logged out successfully' };
  }

  @ApiOperation({ summary: 'Verify Registration OTP' })
  @ApiResponse({ status: 200, description: 'Account activated successfully.' })
  @HttpCode(200)
  @Post('verify-otp')
  async verifyOtp(@Body() verifyOtpDto: VerifyOtpDto) {
    return this.authService.verifyOtp(verifyOtpDto.email, verifyOtpDto.otpCode);
  }

  @ApiBearerAuth()
  @ApiOperation({ summary: 'Request OTP to update email' })
  @UseGuards(JwtAuthGuard)
  @HttpCode(200)
  @Post('request-email-update')
  async requestEmailUpdate(@Body() dto: RequestEmailUpdateDto, @Request() req: any) {
    return this.authService.requestEmailUpdate(req.user.userId, dto.newEmail);
  }

  @ApiBearerAuth()
  @ApiOperation({ summary: 'Verify OTP to update email' })
  @UseGuards(JwtAuthGuard)
  @HttpCode(200)
  @Post('verify-email-update')
  async verifyEmailUpdate(@Body() dto: VerifyEmailUpdateDto, @Request() req: any) {
    return this.authService.verifyEmailUpdate(req.user.userId, dto.newEmail, dto.otpCode);
  }

  @ApiBearerAuth()
  @ApiOperation({ summary: 'Get current user profile' })
  @ApiResponse({ status: 200, description: 'Profile retrieved successfully.' })
  @ApiResponse({ status: 401, description: 'Unauthorised.' })
  @UseGuards(JwtAuthGuard)
  @Get('me')
  async me(@Request() req: any) {
    return this.authService.me(req.user.userId);
  }
}

import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';
import { ConfigService } from '@nestjs/config';
import { ValidationPipe } from '@nestjs/common';
import { GlobalExceptionFilter } from './core/filters/exception.filter';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import cookieParser from 'cookie-parser';
import * as fs from 'fs';
import * as path from 'path';

type CommonConfig = {
  frontend?: { url?: string; port?: string };
  services?: {
    auth?: { url?: string; port?: string };
  };
};

function loadCommonConfig(): CommonConfig {
  const configPath = path.resolve(
    process.cwd(),
    process.env.COMMON_CONFIG_PATH ?? '../../Config/application.yml',
  );

  if (!fs.existsSync(configPath)) {
    return {};
  }

  const root: Record<string, any> = {};
  const stack: Array<{ indent: number; value: Record<string, any> }> = [{ indent: -1, value: root }];

  for (const line of fs.readFileSync(configPath, 'utf8').split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) {
      continue;
    }

    const match = /^(\s*)([A-Za-z0-9_-]+):(?:\s*(.*))?$/.exec(line);
    if (!match) {
      continue;
    }

    const indent = match[1].length;
    const key = match[2];
    const rawValue = (match[3] ?? '').trim();

    while (stack.length > 1 && indent <= stack[stack.length - 1].indent) {
      stack.pop();
    }

    const parent = stack[stack.length - 1].value;
    if (!rawValue) {
      parent[key] = {};
      stack.push({ indent, value: parent[key] });
      continue;
    }

    parent[key] = rawValue.replace(/^['"]|['"]$/g, '');
  }

  return root as CommonConfig;
}

function buildBaseUrl(url?: string, port?: string): string | undefined {
  if (!url) {
    return undefined;
  }

  return port ? `${url}:${port}` : url;
}

async function bootstrap() {
  const app = await NestFactory.create(AppModule);
  const configService = app.get(ConfigService);
  const commonConfig = loadCommonConfig();
  const frontendUrl =
    configService.get<string>('FRONTEND_URL') ??
    buildBaseUrl(commonConfig.frontend?.url, commonConfig.frontend?.port) ??
    'http://localhost:4200';

  app.use(cookieParser());
  app.enableCors({
    origin: frontendUrl,
    credentials: true,
  });

  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      transform: true,
      errorHttpStatusCode: 422,
    }),
  );

  app.useGlobalFilters(new GlobalExceptionFilter());

  const config = new DocumentBuilder()
    .setTitle('Auth service')
    .setVersion('1.0')
    .addBearerAuth()
    .build();
  const document = SwaggerModule.createDocument(app, config);
  SwaggerModule.setup('docs', app, document);

  const port = Number(
    configService.get<string>('PORT') ?? commonConfig.services?.auth?.port ?? 3000,
  );
  await app.listen(port);
}
void bootstrap();

#!/usr/bin/env node
/**
 * Build script that reads .env file and injects environment variables into the Angular build
 * This ensures .env values are available at runtime in the application
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

function parseSimpleYaml(content) {
  const root = {};
  const stack = [{ indent: -1, value: root }];

  content.split(/\r?\n/).forEach(line => {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) {
      return;
    }

    const match = /^(\s*)([A-Za-z0-9_-]+):(?:\s*(.*))?$/.exec(line);
    if (!match) {
      return;
    }

    const indent = match[1].length;
    const key = match[2];
    const rawValue = (match[3] || '').trim();

    while (stack.length > 1 && indent <= stack[stack.length - 1].indent) {
      stack.pop();
    }

    const parent = stack[stack.length - 1].value;
    if (!rawValue) {
      parent[key] = {};
      stack.push({ indent, value: parent[key] });
      return;
    }

    parent[key] = rawValue.replace(/^['"]|['"]$/g, '');
  });

  return root;
}

function buildBaseUrl(serviceConfig) {
  if (!serviceConfig || !serviceConfig.url) {
    return undefined;
  }

  return serviceConfig.port ? `${serviceConfig.url}:${serviceConfig.port}` : serviceConfig.url;
}

// Parse optional .env file overrides
const envPath = path.join(__dirname, '.env');
const commonConfigPath = process.env.COMMON_CONFIG_PATH
  ? path.resolve(__dirname, process.env.COMMON_CONFIG_PATH)
  : path.resolve(__dirname, '..', '..', 'Config', 'application.yml');

const commonConfig = fs.existsSync(commonConfigPath)
  ? parseSimpleYaml(fs.readFileSync(commonConfigPath, 'utf8'))
  : {};

if (!fs.existsSync(commonConfigPath)) {
  console.warn('Warning: Config/application.yml not found. Falling back to legacy frontend defaults.');
}

const envVars = {};

if (fs.existsSync(envPath)) {
  const envContent = fs.readFileSync(envPath, 'utf8');

  envContent.split('\n').forEach(line => {
    const trimmed = line.trim();
    if (trimmed && !trimmed.startsWith('#')) {
      const [key, ...valueParts] = trimmed.split('=');
      if (key) {
        envVars[key] = valueParts.join('=');
      }
    }
  });
}

const defaultAuthApiUrl = buildBaseUrl(commonConfig.services && commonConfig.services.auth)
  || 'http://localhost:8080';
const defaultTradeApiUrl = buildBaseUrl(commonConfig.services && commonConfig.services.order)
  || 'http://localhost:8081';

// Show env vars only in development (not in CI/CD logs)
const isDev = !process.env.CI && envVars['NG_APP_ENVIRONMENT'] !== 'production' && envVars['NG_APP_ENVIRONMENT'] !== 'staging';
if (isDev) {
  console.log('✓ Loaded frontend configuration from Config/application.yml with optional .env overrides:');
  Object.entries(envVars).forEach(([key, value]) => {
    if (key.startsWith('NG_APP_')) {
      console.log(`  ${key}=${value}`);
    }
  });
} else {
  console.log('✓ Loaded configuration from .env');
}

// Create environment.ts file
const environmentContent = `// Auto-generated from .env file during build
// Do not edit manually - will be overwritten on next build

export const environment = {
  authApiUrl: '${envVars['NG_APP_AUTH_API_URL'] || defaultAuthApiUrl}',
  tradeApiUrl: '${envVars['NG_APP_TRADE_API_URL'] || defaultTradeApiUrl}',
  environment: '${envVars['NG_APP_ENVIRONMENT'] || 'development'}',
};
`;

// Validate configuration
const authApiUrl = envVars['NG_APP_AUTH_API_URL'] || defaultAuthApiUrl;
const tradeApiUrl = envVars['NG_APP_TRADE_API_URL'] || defaultTradeApiUrl;
const env = envVars['NG_APP_ENVIRONMENT'] || 'development';

// Warn if using localhost in production
if (env === 'production' || env === 'staging') {
  const hasLocalhost = [authApiUrl, tradeApiUrl].some(url => 
    url && (url.includes('localhost') || url.includes('127.0.0.1') || url.startsWith('http://'))
  );
  
  if (hasLocalhost) {
    console.error(`❌ FATAL: Cannot deploy ${env} with localhost URLs!`);
    console.error(`   Auth API: ${authApiUrl}`);
    console.error(`   Trade API: ${tradeApiUrl}`);
    console.error('   Update .env with proper endpoints');
    process.exit(1);
  }
}

const envDir = path.join(__dirname, 'src/environments');
if (!fs.existsSync(envDir)) {
  fs.mkdirSync(envDir, { recursive: true });
}

fs.writeFileSync(path.join(envDir, 'environment.ts'), environmentContent);
console.log('\n✓ Generated src/environments/environment.ts');

// Build
console.log('\n📦 Building application...\n');
try {
  execSync('ng build', { stdio: 'inherit' });
  console.log('\n✓ Build complete!');
} catch (error) {
  console.error('✗ Build failed');
  process.exit(1);
}

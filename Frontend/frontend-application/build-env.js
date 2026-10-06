#!/usr/bin/env node
/**
 * Build script that reads .env file and injects environment variables into the Angular build
 * This ensures .env values are available at runtime in the application
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

// Parse .env file
const envPath = path.join(__dirname, '.env');
if (!fs.existsSync(envPath)) {
  console.error('❌ FATAL: .env file not found!');
  console.error('   Create .env from .env.example and configure your URLs');
  process.exit(1);
}

const envContent = fs.readFileSync(envPath, 'utf8');
const envVars = {};

envContent.split('\n').forEach(line => {
  const trimmed = line.trim();
  if (trimmed && !trimmed.startsWith('#')) {
    const [key, ...valueParts] = trimmed.split('=');
    if (key) {
      envVars[key] = valueParts.join('=');
    }
  }
});

// Show env vars only in development (not in CI/CD logs)
const isDev = !process.env.CI && envVars['NG_APP_ENVIRONMENT'] !== 'production' && envVars['NG_APP_ENVIRONMENT'] !== 'staging';
if (isDev) {
  console.log('✓ Loaded environment variables from .env:');
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
  authApiUrl: '${envVars['NG_APP_AUTH_API_URL'] || 'http://localhost:3000'}',
  tradeApiUrl: '${envVars['NG_APP_TRADE_API_URL'] || 'http://localhost:8081'}',
  environment: '${envVars['NG_APP_ENVIRONMENT'] || 'development'}',
};
`;

// Validate configuration
const authApiUrl = envVars['NG_APP_AUTH_API_URL'];
const tradeApiUrl = envVars['NG_APP_TRADE_API_URL'];
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

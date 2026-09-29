module.exports = {
  preset: "ts-jest",
  moduleFileExtensions: ['js', 'json', 'ts'],
  rootDir: '.',
  testRegex: 'spec/.*\\.spec\\.ts$',
  testPathIgnorePatterns: ['\\.dto\\.ts$', '\\.entity\\.ts$', '\\.module\\.ts$', 'main.ts'],
  transform: {
    '^.+\\.(t|j)s$': ['ts-jest', { tsconfig: 'tsconfig.spec.json' }],
  },
  transformIgnorePatterns: ['/node_modules/(?!@nestjs/)'],
  collectCoverageFrom: [
    'src/**/*.(t|j)s',
    '!src/**/*.dto.ts',
    '!src/**/*.entity.ts',
    '!src/**/*.module.ts',
    '!src/main.ts',
  ],
  coverageDirectory: '../coverage',
  coverageReporters: ['text', 'lcovonly', 'text-summary', 'json-summary', 'clover'],
  testEnvironment: 'node',
};


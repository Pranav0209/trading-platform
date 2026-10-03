import { rmSync } from 'node:fs';
import { join } from 'node:path';

const generatedRoots = [
  'src/generated/auth',
  'src/generated/trade'
];

const removableEntries = [
  '.gitignore',
  '.openapi-generator-ignore',
  '.openapi-generator',
  'git_push.sh'
];

for (const generatedRoot of generatedRoots) {
  for (const removableEntry of removableEntries) {
    rmSync(join(generatedRoot, removableEntry), {
      force: true,
      recursive: true
    });
  }
}
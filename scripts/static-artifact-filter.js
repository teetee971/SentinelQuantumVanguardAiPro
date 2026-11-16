import { basename } from 'node:path';

/** Runtime sources remain public; Node test programs are not deployment assets. */
export function includePublicArtifact(sourcePath) {
  return !basename(sourcePath).endsWith('.test.js');
}

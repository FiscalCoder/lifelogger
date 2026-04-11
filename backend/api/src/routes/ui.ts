import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import type { Context } from 'hono';

// Loaded once at startup; the file is copied to dist/html/ by the postbuild step
const htmlPath = join(__dirname, '..', 'html', 'speakerNaming.html');
const speakerNamingHtml = readFileSync(htmlPath, 'utf-8');

export async function speakerUiHandler(c: Context): Promise<Response> {
  return new Response(speakerNamingHtml, {
    status: 200,
    headers: { 'Content-Type': 'text/html; charset=utf-8' },
  });
}

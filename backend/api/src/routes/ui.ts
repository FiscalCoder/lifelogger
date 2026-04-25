import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import type { Context } from 'hono';

// Loaded once at startup; the file is copied to dist/html/ by the postbuild step
const htmlPath = join(__dirname, '..', 'html', 'speakerNaming.html');
const speakerNamingHtml = readFileSync(htmlPath, 'utf-8');
const TOKEN_PLACEHOLDER = '__LIFELOGGER_API_TOKEN__';

function extractBearerToken(c: Context): string {
  const authHeader = c.req.header('authorization') ?? '';
  return authHeader.startsWith('Bearer ') ? authHeader.slice('Bearer '.length).trim() : '';
}

export async function speakerUiHandler(c: Context): Promise<Response> {
  const html = speakerNamingHtml.replace(
    TOKEN_PLACEHOLDER,
    () => JSON.stringify(extractBearerToken(c)),
  );
  return new Response(html, {
    status: 200,
    headers: {
      'Content-Type': 'text/html; charset=utf-8',
      'Cache-Control': 'no-store',
    },
  });
}

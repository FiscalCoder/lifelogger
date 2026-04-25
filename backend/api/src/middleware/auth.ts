import { createHash, timingSafeEqual } from 'node:crypto';
import type { Context, MiddlewareHandler } from 'hono';

const AUTH_SCHEME = 'Bearer ';
const UNAUTHORIZED_BODY = { error: 'Unauthorized' } as const;

function hashToken(value: string): Buffer {
  return createHash('sha256').update(value, 'utf8').digest();
}

function tokensMatch(candidateToken: string, expectedToken: string): boolean {
  return timingSafeEqual(hashToken(candidateToken), hashToken(expectedToken));
}

function unauthorizedResponse(c: Context): Response {
  c.header('WWW-Authenticate', 'Bearer');
  return c.json(UNAUTHORIZED_BODY, 401);
}

export const bearerAuth: MiddlewareHandler = async (c, next) => {
  const expectedToken = process.env.API_TOKEN;
  if (!expectedToken) {
    console.error('[auth] API_TOKEN is not configured');
    return c.json({ error: 'Server authentication is not configured' }, 500);
  }
  const authHeader = c.req.header('authorization') ?? '';
  if (!authHeader.startsWith(AUTH_SCHEME)) {
    return unauthorizedResponse(c);
  }
  const candidateToken = authHeader.slice(AUTH_SCHEME.length).trim();
  if (!candidateToken || !tokensMatch(candidateToken, expectedToken)) {
    return unauthorizedResponse(c);
  }
  return next();
};

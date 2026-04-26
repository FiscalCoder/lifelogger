import { randomUUID } from 'node:crypto';
import type { MiddlewareHandler } from 'hono';

const REQUEST_ID_HEADER = 'X-Request-Id';

function firstHeaderValue(value: string | undefined): string {
  return value?.split(',')[0]?.trim() || 'unknown';
}

export function requestLogger(): MiddlewareHandler {
  return async (c, next) => {
    const start = Date.now();
    const requestId = c.req.header('x-request-id') || randomUUID();
    const method = c.req.method;
    const path = c.req.path;
    const clientIp = firstHeaderValue(
      c.req.header('cf-connecting-ip')
        ?? c.req.header('x-forwarded-for')
        ?? c.req.header('x-real-ip'),
    );
    const userAgent = c.req.header('user-agent') ?? 'unknown';
    const authState = c.req.header('authorization') ? 'present' : 'missing';

    c.header(REQUEST_ID_HEADER, requestId);
    console.log(
      `[api] request start id=${requestId} method=${method} path=${path} `
      + `ip=${clientIp} auth=${authState} ua="${userAgent}"`,
    );

    try {
      await next();
    } catch (err) {
      const ms = Date.now() - start;
      console.error(
        `[api] request error id=${requestId} method=${method} path=${path} `
        + `duration=${ms}ms`,
        err,
      );
      throw err;
    } finally {
      const status = c.res.status;
      const ms = Date.now() - start;
      console.log(
        `[api] request end id=${requestId} method=${method} path=${path} `
        + `status=${status} duration=${ms}ms`,
      );
    }
  };
}

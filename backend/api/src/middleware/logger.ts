import { MiddlewareHandler } from 'hono';

export function requestLogger(): MiddlewareHandler {
  return async (c, next) => {
    const start = Date.now();
    const method = c.req.method;
    const path = c.req.path;

    await next();

    const status = c.res.status;
    const ms = Date.now() - start;
    console.log(`[api] ${method} ${path} ${status} ${ms}ms`);
  };
}

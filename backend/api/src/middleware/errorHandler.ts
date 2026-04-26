import { ErrorHandler } from 'hono';

export const errorHandler: ErrorHandler = (err, c) => {
  const error = err as Error & { code?: unknown };
  console.error('[api] Unhandled error', {
    method: c.req.method,
    path: c.req.path,
    name: error.name,
    message: error.message,
    code: error.code,
    stack: error.stack,
  });
  return c.json(
    { error: 'Internal server error', message: err.message },
    500,
  );
};

import { ErrorHandler } from 'hono';

export const errorHandler: ErrorHandler = (err, c) => {
  console.error(`[api] Unhandled error on ${c.req.method} ${c.req.path}:`, err);
  return c.json(
    { error: 'Internal server error', message: err.message },
    500,
  );
};

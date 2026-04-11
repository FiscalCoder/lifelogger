import { Hono } from 'hono';
import { search } from '../services/queryService';

export const queryRouter = new Hono();

// POST /query — semantic search over transcript segments
queryRouter.post('/', async (c) => {
  let body: { query?: unknown };
  try {
    body = await c.req.json();
  } catch {
    return c.json({ error: 'Expected JSON body' }, 400);
  }

  const { query } = body;

  if (!query || typeof query !== 'string' || query.trim().length === 0) {
    return c.json({ error: 'query is required and must be a non-empty string' }, 400);
  }
  if (query.trim().length > 500) {
    return c.json({ error: 'query must be 500 characters or fewer' }, 400);
  }

  const results = await search(query.trim());
  return c.json({ query: query.trim(), results, total: results.length });
});

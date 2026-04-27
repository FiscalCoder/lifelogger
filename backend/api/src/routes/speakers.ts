import { Hono } from 'hono';
import type { Context } from 'hono';
import {
  listSpeakers,
  listUnknowns,
  nameSpeaker,
  deleteSpeaker,
  markUnknownAsMedia,
} from '../services/speakerRegistry';

export const speakersRouter = new Hono();

// GET /speakers — list all known speakers
speakersRouter.get('/', async (c) => {
  const speakers = await listSpeakers();
  return c.json(speakers);
});

// POST /speakers/name — assign a real name to an unknown speaker
speakersRouter.post('/name', async (c) => {
  let body: { unknownId?: unknown; name?: unknown };
  try {
    body = await c.req.json();
  } catch {
    return c.json({ error: 'Expected JSON body' }, 400);
  }

  const { unknownId, name } = body;

  if (!unknownId || typeof unknownId !== 'string' || unknownId.trim() === '') {
    return c.json({ error: 'unknownId is required' }, 400);
  }
  if (!name || typeof name !== 'string' || name.trim() === '') {
    return c.json({ error: 'name is required and must be non-empty' }, 400);
  }

  try {
    const result = await nameSpeaker(unknownId.trim(), name.trim());
    return c.json(result);
  } catch (err: unknown) {
    const e = err as Error & { status?: number };
    return c.json({ error: e.message }, (e.status as 400 | 404 | 409) ?? 500);
  }
});

// DELETE /speakers/:id — remove a speaker profile
speakersRouter.delete('/:id', async (c) => {
  const id = c.req.param('id');
  try {
    await deleteSpeaker(id);
    return c.json({ success: true });
  } catch (err: unknown) {
    const e = err as Error & { status?: number };
    return c.json({ error: e.message }, (e.status as 404) ?? 500);
  }
});

// GET /unknowns — exported as a standalone handler for the root-level route
export async function unknownsHandler(c: Context) {
  const unknowns = await listUnknowns();
  return c.json(unknowns);
}

// POST /unknowns/:id/mark-media — resolve an unknown voice as media/public audio
export async function markMediaHandler(c: Context) {
  const id = c.req.param('id');
  if (!id || id.trim() === '') {
    return c.json({ error: 'id is required' }, 400);
  }
  try {
    const result = await markUnknownAsMedia(id.trim());
    return c.json(result);
  } catch (err: unknown) {
    const e = err as Error & { status?: number };
    return c.json({ error: e.message }, (e.status as 404 | 409) ?? 500);
  }
}

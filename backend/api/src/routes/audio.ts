import { Hono } from 'hono';
import { streamSample } from '../services/audioStorage';

export const audioRouter = new Hono();

// GET /audio/:filename/sample — stream a 10-second speaker sample clip
audioRouter.get('/:filename/sample', async (c) => {
  const filename = c.req.param('filename');

  if (!filename || filename.includes('..') || filename.includes('/')) {
    return c.json({ error: 'Invalid filename' }, 400);
  }

  const { buffer, exists } = await streamSample(filename);

  if (!exists) {
    return c.json({ error: 'Audio sample not found' }, 404);
  }

  return new Response(new Uint8Array(buffer), {
    status: 200,
    headers: {
      'Content-Type': 'audio/aac',
      'Content-Length': String(buffer.length),
      'Cache-Control': 'no-store',
    },
  });
});

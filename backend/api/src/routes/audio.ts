import { Hono } from 'hono';
import { streamSample, streamSourceSegment } from '../services/audioStorage';

export const audioRouter = new Hono();
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

// GET /audio/:filename/sample — stream a 10-second speaker sample clip
audioRouter.get('/:filename/sample', async (c) => {
  const filename = c.req.param('filename');

  if (!filename || filename.includes('..') || filename.includes('/')) {
    return c.json({ error: 'Invalid filename' }, 400);
  }

  const { buffer, exists, contentType } = await streamSample(filename);

  if (!exists) {
    return c.json({ error: 'Audio sample not found' }, 404);
  }

  return new Response(new Uint8Array(buffer), {
    status: 200,
    headers: {
      'Content-Type': contentType,
      'Content-Length': String(buffer.length),
      'Cache-Control': 'no-store',
    },
  });
});

// GET /audio/segments/:segmentId — stream the source chunk for exact timestamp review
audioRouter.get('/segments/:segmentId', async (c) => {
  const segmentId = c.req.param('segmentId');
  if (!UUID_PATTERN.test(segmentId)) {
    return c.json({ error: 'Invalid segment id' }, 400);
  }
  const segment = await streamSourceSegment(segmentId);
  if (!segment.exists) {
    return c.json({ error: 'Source audio not found' }, 404);
  }
  return new Response(new Uint8Array(segment.buffer), {
    status: 200,
    headers: {
      'Content-Type': segment.contentType,
      'Content-Length': String(segment.buffer.length),
      'Cache-Control': 'no-store',
      'X-LifeLogger-Source-File': segment.sourceFile ?? '',
      'X-LifeLogger-Segment-Start': String(segment.startSeconds ?? ''),
      'X-LifeLogger-Segment-End': String(segment.endSeconds ?? ''),
    },
  });
});

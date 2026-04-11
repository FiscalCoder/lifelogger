import { Hono } from 'hono';
import { db } from '../db/client';
import { uploadQueue } from '../db/schema';
import { saveChunk } from '../services/audioStorage';

export const uploadRouter = new Hono();

uploadRouter.post('/', async (c) => {
  let formData: FormData;
  try {
    formData = await c.req.formData();
  } catch {
    return c.json({ error: 'Expected multipart/form-data body' }, 400);
  }

  const deviceId = formData.get('device_id');
  const recordedAtRaw = formData.get('recorded_at');
  const durationRaw = formData.get('duration_seconds');
  const file = formData.get('file');

  // Validate required fields
  if (!deviceId || typeof deviceId !== 'string' || deviceId.trim() === '') {
    return c.json({ error: 'device_id is required and must be non-empty' }, 400);
  }
  if (!recordedAtRaw || typeof recordedAtRaw !== 'string') {
    return c.json({ error: 'recorded_at is required (ISO8601 string)' }, 400);
  }

  const recordedAt = new Date(recordedAtRaw);
  if (isNaN(recordedAt.getTime())) {
    return c.json({ error: 'recorded_at must be a valid ISO8601 date string' }, 400);
  }

  const durationSeconds = parseFloat(durationRaw as string);
  if (isNaN(durationSeconds) || durationSeconds <= 0.1 || durationSeconds > 3600) {
    return c.json(
      { error: 'duration_seconds must be a number between 0.1 and 3600' },
      400,
    );
  }

  if (!file || !(file instanceof File) || file.size === 0) {
    return c.json({ error: 'file is required and must be non-empty' }, 400);
  }

  const contentType = file.type;
  if (contentType && !['audio/aac', 'application/octet-stream', ''].includes(contentType)) {
    return c.json(
      { error: 'file must be audio/aac or application/octet-stream' },
      400,
    );
  }

  // Write to disk
  const arrayBuffer = await file.arrayBuffer();
  const { filePath } = await saveChunk(arrayBuffer, deviceId.trim(), recordedAtRaw);

  // Insert into upload_queue
  const [row] = await db
    .insert(uploadQueue)
    .values({
      deviceId: deviceId.trim(),
      filePath,
      recordedAt,
      durationSeconds,
      status: 'received',
    })
    .returning({ id: uploadQueue.id });

  return c.json({ id: row.id, status: 'received' }, 200);
});

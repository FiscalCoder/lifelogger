import 'dotenv/config';
import { serve } from '@hono/node-server';
import { Hono } from 'hono';
import { requestLogger } from './middleware/logger';
import { errorHandler } from './middleware/errorHandler';
import { bearerAuth } from './middleware/auth';
import { uploadRouter } from './routes/upload';
import { speakersRouter, unknownsHandler, markMediaHandler } from './routes/speakers';
import { audioRouter } from './routes/audio';
import { queryRouter } from './routes/query';
import { verifyDatabaseConnection } from './db/client';

const REQUIRED_ENV = [
  'DATABASE_URL',
  'API_TOKEN',
  'AUDIO_PENDING_DIR',
  'AUDIO_ARCHIVE_DIR',
  'AUDIO_SAMPLE_DIR',
];

for (const key of REQUIRED_ENV) {
  if (!process.env[key]) {
    throw new Error(`Missing required environment variable: ${key}`);
  }
}

const app = new Hono();

app.use('*', requestLogger());

app.get('/health', (c) => c.json({ status: 'ok' }));

app.use('*', bearerAuth);

app.route('/upload', uploadRouter);
app.route('/speakers', speakersRouter);
app.route('/audio', audioRouter);
app.route('/query', queryRouter);
app.get('/unknowns', unknownsHandler);
app.post('/unknowns/:id/mark-media', markMediaHandler);

app.onError(errorHandler);

const port = Number(process.env.PORT) || 8000;

serve({ fetch: app.fetch, port }, () => {
  console.log(`[api] Listening on port ${port}`);
  void verifyDatabaseConnection();
});

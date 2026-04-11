import {
  pgTable,
  uuid,
  text,
  timestamp,
  integer,
  boolean,
  real,
} from 'drizzle-orm/pg-core';
import { customType } from 'drizzle-orm/pg-core';

// pgvector custom type — stores as postgres vector literal [x,y,z,...]
const vectorType = customType<{
  data: number[];
  driverData: string;
  config: { dimensions: number };
}>({
  dataType(config) {
    return `vector(${config?.dimensions ?? 1536})`;
  },
  toDriver(value: number[]): string {
    return `[${value.join(',')}]`;
  },
  fromDriver(value: string): number[] {
    return value.slice(1, -1).split(',').map(Number);
  },
});

export const vector = (name: string, config: { dimensions: number }) =>
  vectorType(name, config);

// Known, named speakers
export const speakerProfiles = pgTable('speaker_profiles', {
  id: uuid('id').defaultRandom().primaryKey(),
  name: text('name').notNull().unique(),
  embedding: vector('embedding', { dimensions: 192 }),
  sampleCount: integer('sample_count').default(1).notNull(),
  createdAt: timestamp('created_at').defaultNow().notNull(),
  updatedAt: timestamp('updated_at').defaultNow().notNull(),
});

// Speakers flagged for naming
export const unknownSpeakers = pgTable('unknown_speakers', {
  id: uuid('id').defaultRandom().primaryKey(),
  tempName: text('temp_name').notNull(),
  embedding: vector('embedding', { dimensions: 192 }),
  audioSample: text('audio_sample'),
  recordedAt: timestamp('recorded_at').notNull(),
  resolved: boolean('resolved').default(false).notNull(),
});

// Final assembled transcripts
export const transcriptSegments = pgTable('transcript_segments', {
  id: uuid('id').defaultRandom().primaryKey(),
  speaker: text('speaker').notNull(),
  text: text('text').notNull(),
  language: text('language'),
  embedding: vector('embedding', { dimensions: 768 }),
  recordedAt: timestamp('recorded_at').notNull(),
  sourceFile: text('source_file').notNull(),
  startTime: text('start_time'),
  endTime: text('end_time'),
});

// Upload queue — tracks chunks received from mobile
export const uploadQueue = pgTable('upload_queue', {
  id: uuid('id').defaultRandom().primaryKey(),
  deviceId: text('device_id').notNull(),
  filePath: text('file_path').notNull(),
  recordedAt: timestamp('recorded_at').notNull(),
  durationSeconds: real('duration_seconds'),
  processedAt: timestamp('processed_at'),
  status: text('status').default('received').notNull(),
});

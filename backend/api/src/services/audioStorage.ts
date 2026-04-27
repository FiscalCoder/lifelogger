import { promises as fs } from 'node:fs';
import path from 'node:path';
import { sql } from 'drizzle-orm';
import { db } from '../db/client';

export type SegmentAudioResult = {
  buffer: Buffer;
  exists: boolean;
  sourceFile: string | null;
  startSeconds: number | null;
  endSeconds: number | null;
  contentType: string;
};

type SegmentAudioRow = {
  source_file: string | null;
  start_seconds: number | null;
  end_seconds: number | null;
  file_path: string | null;
};

function pendingDir(dateStr: string): string {
  return path.join(process.env.AUDIO_PENDING_DIR!, dateStr);
}

function archiveDir(dateStr: string): string {
  return path.join(process.env.AUDIO_ARCHIVE_DIR!, dateStr);
}

function samplePath(filename: string): string {
  return path.join(process.env.AUDIO_SAMPLE_DIR!, filename);
}

function contentTypeForFile(filename: string): string {
  const extension = path.extname(filename).toLowerCase();
  if (extension === '.wav') return 'audio/wav';
  if (extension === '.aac' || extension === '.m4a' || extension === '.mp4') return 'audio/mp4';
  return 'audio/aac';
}

// Extract YYYY-MM-DD from an ISO8601 string using the date portion only
function dateDirFromIso(isoString: string): string {
  const d = new Date(isoString);
  const yyyy = d.getUTCFullYear();
  const mm = String(d.getUTCMonth() + 1).padStart(2, '0');
  const dd = String(d.getUTCDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}

export interface SaveChunkResult {
  filePath: string;
  dateDir: string;
}

export async function saveChunk(
  fileData: ArrayBuffer,
  deviceId: string,
  recordedAt: string,
): Promise<SaveChunkResult> {
  const dateDir = dateDirFromIso(recordedAt);
  const dir = pendingDir(dateDir);
  await fs.mkdir(dir, { recursive: true });

  // Filename: deviceId_timestamp_randomSuffix.aac
  const ts = new Date(recordedAt).getTime();
  const suffix = Math.random().toString(36).slice(2, 8);
  const filename = `${deviceId}_${ts}_${suffix}.aac`;
  const filePath = path.join(dir, filename);

  await fs.writeFile(filePath, Buffer.from(fileData));

  return { filePath, dateDir };
}

export async function streamSample(
  filename: string,
): Promise<{ buffer: Buffer; exists: boolean; contentType: string }> {
  const fullPath = samplePath(filename);
  try {
    const buffer = await fs.readFile(fullPath);
    return { buffer, exists: true, contentType: contentTypeForFile(filename) };
  } catch {
    return { buffer: Buffer.alloc(0), exists: false, contentType: contentTypeForFile(filename) };
  }
}

export async function streamSourceSegment(segmentId: string): Promise<SegmentAudioResult> {
  const rows = await db.execute<SegmentAudioRow>(sql`
    SELECT
      ts.source_file,
      ts.start_seconds,
      ts.end_seconds,
      COALESCE(
        uq.file_path,
        (
          SELECT fallback.file_path
          FROM upload_queue fallback
          WHERE fallback.file_path = ts.source_file
             OR fallback.file_path LIKE ('%/' || ts.source_file)
          ORDER BY fallback.recorded_at DESC
          LIMIT 1
        )
      ) AS file_path
    FROM transcript_segments ts
    LEFT JOIN upload_queue uq ON uq.id = ts.source_upload_id
    WHERE ts.id = ${segmentId}::uuid
    LIMIT 1
  `);
  const row = rows.rows[0];
  if (!row?.file_path) {
    return {
      buffer: Buffer.alloc(0),
      exists: false,
      sourceFile: row?.source_file ?? null,
      startSeconds: row?.start_seconds ?? null,
      endSeconds: row?.end_seconds ?? null,
      contentType: 'audio/aac',
    };
  }
  try {
    const buffer = await fs.readFile(row.file_path);
    return {
      buffer,
      exists: true,
      sourceFile: row.source_file,
      startSeconds: row.start_seconds,
      endSeconds: row.end_seconds,
      contentType: contentTypeForFile(row.file_path),
    };
  } catch {
    return {
      buffer: Buffer.alloc(0),
      exists: false,
      sourceFile: row.source_file,
      startSeconds: row.start_seconds,
      endSeconds: row.end_seconds,
      contentType: contentTypeForFile(row.file_path),
    };
  }
}

export { pendingDir, archiveDir, samplePath };

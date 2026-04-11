import { promises as fs } from 'node:fs';
import path from 'node:path';

function pendingDir(dateStr: string): string {
  return path.join(process.env.AUDIO_PENDING_DIR!, dateStr);
}

function archiveDir(dateStr: string): string {
  return path.join(process.env.AUDIO_ARCHIVE_DIR!, dateStr);
}

function samplePath(filename: string): string {
  return path.join(process.env.AUDIO_SAMPLE_DIR!, filename);
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
): Promise<{ buffer: Buffer; exists: boolean }> {
  const fullPath = samplePath(filename);
  try {
    const buffer = await fs.readFile(fullPath);
    return { buffer, exists: true };
  } catch {
    return { buffer: Buffer.alloc(0), exists: false };
  }
}

export { pendingDir, archiveDir, samplePath };

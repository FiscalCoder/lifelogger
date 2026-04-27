export interface UploadQueueRow {
  id: string;
  deviceId: string;
  filePath: string;
  recordedAt: Date;
  durationSeconds: number | null;
  processedAt: Date | null;
  status: 'received' | 'processing' | 'done' | 'failed';
}

export interface SpeakerProfileRow {
  id: string;
  name: string;
  embedding: number[] | null;
  sampleCount: number;
  createdAt: Date;
  updatedAt: Date;
}

export interface UnknownSpeakerRow {
  id: string;
  tempName: string;
  embedding: number[] | null;
  audioSample: string | null;
  recordedAt: Date;
  resolved: boolean;
  resolutionKind: string | null;
  resolvedAt: Date | null;
}

export interface TranscriptSegmentRow {
  id: string;
  speaker: string;
  text: string;
  language: string | null;
  embedding: number[] | null;
  recordedAt: Date;
  sourceFile: string;
  startTime: string | null;
  endTime: string | null;
  sourceKind: string;
  excludeFromRag: boolean;
  sourceUploadId: string | null;
  startSeconds: number | null;
  endSeconds: number | null;
}

export interface UploadResult {
  id: string;
  status: string;
}

export interface QueryResult {
  speaker: string;
  text: string;
  recordedAt: Date;
  similarity: number;
  language: string | null;
  startTime: string | null;
  id: string;
  sourceFile: string;
  sourceKind: string;
  startSeconds: number | null;
  endSeconds: number | null;
}

export interface NameSpeakerBody {
  unknownId: string;
  name: string;
}

import { eq, sql } from 'drizzle-orm';
import { db } from '../db/client';
import { speakerProfiles, unknownSpeakers, transcriptSegments } from '../db/schema';

const SIMILARITY_THRESHOLD = parseFloat(
  process.env.SPEAKER_MATCH_THRESHOLD ?? '0.82',
);

export interface SpeakerMatch {
  id: string;
  name: string;
  similarity: number;
}

// Find the closest speaker profile for a given 192-dim embedding.
// Returns null if no profile exists or best match is below threshold.
export async function lookupSpeaker(
  embedding: number[],
): Promise<SpeakerMatch | null> {
  const vectorLiteral = `[${embedding.join(',')}]`;

  const rows = await db.execute<{ id: string; name: string; similarity: number }>(
    sql`
      SELECT id, name,
             1 - (embedding <=> ${vectorLiteral}::vector) AS similarity
      FROM speaker_profiles
      ORDER BY embedding <=> ${vectorLiteral}::vector ASC
      LIMIT 1
    `,
  );

  if (rows.rows.length === 0) return null;
  const row = rows.rows[0];
  if (row.similarity < SIMILARITY_THRESHOLD) return null;
  return { id: row.id, name: row.name, similarity: row.similarity };
}

// Rolling-average update: blend new_embedding into the stored profile.
export async function updateSpeakerEmbedding(
  speakerId: string,
  newEmbedding: number[],
  currentCount: number,
): Promise<void> {
  const vectorLiteral = `[${newEmbedding.join(',')}]`;
  await db.execute(sql`
    UPDATE speaker_profiles
    SET embedding = (
          (embedding * ${currentCount} + ${vectorLiteral}::vector) / ${currentCount + 1}
        ),
        sample_count = ${currentCount + 1},
        updated_at = NOW()
    WHERE id = ${speakerId}::uuid
  `);
}

export async function listSpeakers() {
  return db
    .select({
      id: speakerProfiles.id,
      name: speakerProfiles.name,
      sampleCount: speakerProfiles.sampleCount,
      createdAt: speakerProfiles.createdAt,
    })
    .from(speakerProfiles)
    .orderBy(speakerProfiles.name);
}

export async function listUnknowns() {
  return db
    .select({
      id: unknownSpeakers.id,
      tempName: unknownSpeakers.tempName,
      audioSample: unknownSpeakers.audioSample,
      recordedAt: unknownSpeakers.recordedAt,
      resolutionKind: unknownSpeakers.resolutionKind,
    })
    .from(unknownSpeakers)
    .where(eq(unknownSpeakers.resolved, false))
    .orderBy(unknownSpeakers.recordedAt);
}

export async function nameSpeaker(
  unknownId: string,
  name: string,
): Promise<{ success: true }> {
  // Load the unknown record
  const [unknown] = await db
    .select()
    .from(unknownSpeakers)
    .where(eq(unknownSpeakers.id, unknownId))
    .limit(1);

  if (!unknown) {
    throw Object.assign(new Error('Unknown speaker not found'), { status: 404 });
  }
  if (unknown.resolved) {
    throw Object.assign(new Error('Speaker already resolved'), { status: 409 });
  }

  // Check whether a profile with this name already exists
  const [existing] = await db
    .select()
    .from(speakerProfiles)
    .where(eq(speakerProfiles.name, name))
    .limit(1);

  if (existing) {
    // Merge embedding via rolling average if both have embeddings
    if (existing.embedding && unknown.embedding) {
      await updateSpeakerEmbedding(
        existing.id,
        unknown.embedding,
        existing.sampleCount,
      );
    }
  } else {
    // Create new profile
    await db.insert(speakerProfiles).values({
      name,
      embedding: unknown.embedding ?? undefined,
      sampleCount: 1,
    });
  }

  // Mark unknown resolved
  await db
    .update(unknownSpeakers)
    .set({
      resolved: true,
      resolutionKind: 'person',
      resolvedAt: sql`NOW()`,
    })
    .where(eq(unknownSpeakers.id, unknownId));

  // Reattribute transcript segments that were labeled with the temp name
  await db
    .update(transcriptSegments)
    .set({ speaker: name })
    .where(eq(transcriptSegments.speaker, unknown.tempName));

  return { success: true };
}

export async function markUnknownAsMedia(unknownId: string): Promise<{ success: true }> {
  const [unknown] = await db
    .select()
    .from(unknownSpeakers)
    .where(eq(unknownSpeakers.id, unknownId))
    .limit(1);
  if (!unknown) {
    throw Object.assign(new Error('Unknown speaker not found'), { status: 404 });
  }
  if (unknown.resolved) {
    throw Object.assign(new Error('Speaker already resolved'), { status: 409 });
  }
  await db
    .update(unknownSpeakers)
    .set({
      resolved: true,
      resolutionKind: 'media_public',
      resolvedAt: sql`NOW()`,
    })
    .where(eq(unknownSpeakers.id, unknownId));
  await db
    .update(transcriptSegments)
    .set({
      sourceKind: 'media_public',
      excludeFromRag: false,
    })
    .where(eq(transcriptSegments.speaker, unknown.tempName));
  return { success: true };
}

export async function deleteSpeaker(speakerId: string): Promise<void> {
  const result = await db
    .delete(speakerProfiles)
    .where(eq(speakerProfiles.id, speakerId));

  if ((result as unknown as { rowCount: number }).rowCount === 0) {
    throw Object.assign(new Error('Speaker not found'), { status: 404 });
  }
}

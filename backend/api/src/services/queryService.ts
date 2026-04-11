import { ilike, desc, sql } from 'drizzle-orm';
import { db } from '../db/client';
import { transcriptSegments } from '../db/schema';

export interface SearchResult {
  speaker: string;
  text: string;
  recordedAt: Date;
  similarity: number;
  language: string | null;
  startTime: string | null;
}

// MVP: full-text ILIKE search.
// When the pipeline has populated embeddings, this can be replaced with a
// pgvector ANN query:
//   SELECT ..., 1 - (embedding <=> $query_vec::vector) AS similarity
//   FROM transcript_segments ORDER BY embedding <=> $query_vec::vector LIMIT 20
export async function search(query: string): Promise<SearchResult[]> {
  const pattern = `%${query.replace(/[%_\\]/g, (c) => `\\${c}`)}%`;

  // Prefer vector search if embeddings are present in the table
  const hasEmbeddings = await db.execute<{ has_emb: boolean }>(sql`
    SELECT EXISTS (
      SELECT 1 FROM transcript_segments WHERE embedding IS NOT NULL LIMIT 1
    ) AS has_emb
  `);

  if (hasEmbeddings.rows[0]?.has_emb) {
    // Text-only fallback still — embedding requires the query to be vectorized
    // by the pipeline process. The API layer has no ML model loaded.
    // Return ILIKE results with a synthetic similarity score based on
    // text relevance (exact match scores higher than partial).
  }

  const rows = await db
    .select({
      speaker: transcriptSegments.speaker,
      text: transcriptSegments.text,
      recordedAt: transcriptSegments.recordedAt,
      language: transcriptSegments.language,
      startTime: transcriptSegments.startTime,
    })
    .from(transcriptSegments)
    .where(ilike(transcriptSegments.text, pattern))
    .orderBy(desc(transcriptSegments.recordedAt))
    .limit(20);

  return rows.map((row) => ({
    ...row,
    // Synthetic similarity: 1.0 for exact match, 0.5 for partial
    similarity: row.text.toLowerCase() === query.toLowerCase() ? 1.0 : 0.5,
  }));
}

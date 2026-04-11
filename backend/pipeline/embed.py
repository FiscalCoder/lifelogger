"""
Step 5 of the nightly pipeline: generate 768-dim text embeddings for
transcript_segments that do not yet have an embedding and update the DB.

Uses sentence-transformers/paraphrase-multilingual-mpnet-base-v2 which
handles Tamil, English, and Tamil-English code-switching natively.

Usage:
    python embed.py 2026-04-10

Reads:  DB: transcript_segments WHERE embedding IS NULL AND DATE(recorded_at) = date
Writes: DB: transcript_segments.embedding (updated in-place)
"""

import sys
from datetime import datetime, timezone
from pathlib import Path

import numpy as np

from config import (
    AUDIO_ARCHIVE_DIR,
    EMBEDDING_BATCH_SIZE,
    EMBEDDING_MODEL,
    EMBEDDING_MODEL_DIR,
    MIN_SEGMENT_WORDS,
)
from db import execute, fetch_all, get_connection


# Tamil Unicode block range: U+0B80–U+0BFF
_TAMIL_START = 0x0B80
_TAMIL_END = 0x0BFF


def _detect_language_tag(text: str, whisper_lang: str | None) -> str:
    """
    Normalise the language tag for a segment.
    - If whisper reported a language, use it (mapped to 'ta'/'en'/other).
    - If None: detect from Unicode script presence.
    - If both scripts present: 'mixed'.
    """
    has_tamil = any(_TAMIL_START <= ord(ch) <= _TAMIL_END for ch in text)
    has_latin = any(ch.isascii() and ch.isalpha() for ch in text)

    if has_tamil and has_latin:
        return 'mixed'
    if has_tamil:
        return 'ta'
    if whisper_lang:
        return whisper_lang
    return 'en'


def _load_model():
    from sentence_transformers import SentenceTransformer  # type: ignore

    model = SentenceTransformer(EMBEDDING_MODEL, cache_folder=EMBEDDING_MODEL_DIR)
    return model


def _is_embeddable(text: str) -> bool:
    """Return True if the segment has enough content to be worth embedding."""
    words = text.split()
    return len(words) >= MIN_SEGMENT_WORDS


def run_embedding(date: str) -> None:
    """
    Find all transcript_segments for *date* without embeddings,
    generate embeddings in batches, and update the DB.
    """
    # Compute date bounds
    try:
        parts = [int(p) for p in date.split('-')]
        day_start = datetime(*parts, tzinfo=timezone.utc)
    except (ValueError, TypeError):
        print(f'[embed] Invalid date: {date}')
        return

    rows = fetch_all(
        """
        SELECT id, text, language
        FROM transcript_segments
        WHERE embedding IS NULL
          AND recorded_at >= %s::timestamptz
          AND recorded_at < %s::timestamptz + INTERVAL '1 day'
        ORDER BY recorded_at ASC
        """,
        (day_start.isoformat(), day_start.isoformat()),
    )

    if not rows:
        print(f'[embed] No unembedded segments for {date}')
        return

    print(f'[embed] Generating embeddings for {len(rows)} segment(s)')

    # Filter out segments too short to embed
    embeddable = [r for r in rows if _is_embeddable(r['text'])]
    skipped = len(rows) - len(embeddable)
    if skipped:
        print(f'[embed]   Skipping {skipped} segment(s) (fewer than {MIN_SEGMENT_WORDS} words)')

    if not embeddable:
        return

    model = _load_model()

    texts = [r['text'] for r in embeddable]
    ids = [str(r['id']) for r in embeddable]

    # Batch encode — more efficient than per-segment calls
    embeddings: np.ndarray = model.encode(
        texts,
        batch_size=EMBEDDING_BATCH_SIZE,
        normalize_embeddings=True,
        show_progress_bar=len(texts) > 50,
    )

    updated = 0
    with get_connection() as conn:
        for row_id, text, vec, row in zip(ids, texts, embeddings, embeddable):
            vec_list = vec.tolist()
            vec_literal = f"[{','.join(str(x) for x in vec_list)}]"

            # Refresh language tag using detected script
            lang_tag = _detect_language_tag(text, row.get('language'))

            execute(
                """
                UPDATE transcript_segments
                SET embedding = %s::vector,
                    language  = %s
                WHERE id = %s::uuid
                """,
                (vec_literal, lang_tag, row_id),
                conn=conn,
            )
            updated += 1

    print(f'[embed] Updated {updated} segment(s) with embeddings for {date}')


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print('Usage: python embed.py YYYY-MM-DD')
        sys.exit(1)
    run_embedding(sys.argv[1])

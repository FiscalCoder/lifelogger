"""
All pipeline constants and configuration.
Every value can be overridden via environment variables.
"""
import os


def _env_bool(name: str, default: bool) -> bool:
    """Parse a boolean environment variable with predictable defaults."""
    raw = os.environ.get(name)
    if raw is None:
        return default
    return raw.strip().lower() in {'1', 'true', 'yes', 'on'}


# ── Audio directories ─────────────────────────────────────────────────────────
AUDIO_PENDING_DIR: str = os.environ.get('AUDIO_PENDING_DIR', '/audio/pending')
AUDIO_ARCHIVE_DIR: str = os.environ.get('AUDIO_ARCHIVE_DIR', '/audio/archive')
AUDIO_SAMPLE_DIR: str = os.environ.get('AUDIO_SAMPLE_DIR', '/audio/samples')

# ── Database ──────────────────────────────────────────────────────────────────
DB_URL: str = os.environ.get(
    'DATABASE_URL', 'postgres://lifelogger:changeme@localhost:5432/lifelogger'
)

# ── Model identifiers ─────────────────────────────────────────────────────────
WHISPER_MODEL: str = os.environ.get('WHISPER_MODEL', 'large-v3')
PYANNOTE_MODEL: str = os.environ.get(
    'PYANNOTE_MODEL', 'pyannote/speaker-diarization-3.1'
)
DIARIZATION_MODE: str = os.environ.get('DIARIZATION_MODE', 'pyannote').strip().lower()
DIARIZATION_HEARTBEAT_SECONDS: int = int(
    os.environ.get('DIARIZATION_HEARTBEAT_SECONDS', '30')
)
ECAPA_MODEL: str = os.environ.get(
    'ECAPA_MODEL', 'speechbrain/spkrec-ecapa-voxceleb'
)
EMBEDDING_MODEL: str = os.environ.get(
    'EMBEDDING_MODEL',
    'sentence-transformers/paraphrase-multilingual-mpnet-base-v2',
)

# ── Local model cache directories ─────────────────────────────────────────────
WHISPER_MODEL_DIR: str = os.environ.get(
    'WHISPER_MODEL_DIR', '/app/models/faster-whisper'
)
ECAPA_MODEL_DIR: str = os.environ.get(
    'ECAPA_MODEL_DIR', '/app/models/speechbrain-ecapa'
)
EMBEDDING_MODEL_DIR: str = os.environ.get(
    'EMBEDDING_MODEL_DIR', '/app/models/multilingual-mpnet'
)
PYANNOTE_MODEL_DIR: str = os.environ.get(
    'PYANNOTE_MODEL_DIR', '/app/models/pyannote'
)

# ── HuggingFace token (required for pyannote) ─────────────────────────────────
HF_TOKEN: str = os.environ.get('HF_TOKEN', '')

# ── Speaker identification ────────────────────────────────────────────────────
# Cosine similarity threshold: match accepted when similarity >= threshold
ECAPA_SIMILARITY_THRESHOLD: float = float(
    os.environ.get('SPEAKER_MATCH_THRESHOLD', '0.82')
)

# Duration of the speaker sample clip saved for each new speaker (seconds)
SPEAKER_SAMPLE_DURATION: float = float(
    os.environ.get('SPEAKER_SAMPLE_DURATION', '10.0')
)

# ── Transcription ─────────────────────────────────────────────────────────────
MAX_WORKERS: int = int(os.environ.get('MAX_WORKERS', '2'))
WHISPER_BEAM_SIZE: int = int(os.environ.get('WHISPER_BEAM_SIZE', '5'))
WHISPER_TASK: str = os.environ.get('WHISPER_TASK', 'transcribe')
WHISPER_VAD_FILTER: bool = _env_bool('WHISPER_VAD_FILTER', True)
WHISPER_LANGUAGE: str = os.environ.get('WHISPER_LANGUAGE', '')
WHISPER_INITIAL_PROMPT: str = os.environ.get(
    'WHISPER_INITIAL_PROMPT',
    'Transcribe exactly. Preserve Tamil, English, and code-switching. Do not translate.',
)
WHISPER_CONDITION_ON_PREVIOUS_TEXT: bool = _env_bool(
    'WHISPER_CONDITION_ON_PREVIOUS_TEXT',
    False,
)

# Filter segments where no-speech probability exceeds this threshold
NO_SPEECH_PROB_THRESHOLD: float = float(
    os.environ.get('NO_SPEECH_PROB_THRESHOLD', '0.6')
)

# ── Text embedding ────────────────────────────────────────────────────────────
EMBEDDING_BATCH_SIZE: int = int(os.environ.get('EMBEDDING_BATCH_SIZE', '64'))
MIN_SEGMENT_WORDS: int = int(os.environ.get('MIN_SEGMENT_WORDS', '3'))

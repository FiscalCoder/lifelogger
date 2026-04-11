"""
psycopg2 connection helpers for the pipeline scripts.
All pipeline DB access goes through this module — no raw SQL elsewhere.
"""
import contextlib
from typing import Any, Generator, Optional

import psycopg2
import psycopg2.extras

from config import DB_URL


@contextlib.contextmanager
def get_connection() -> Generator[psycopg2.extensions.connection, None, None]:
    """Context manager that yields a committed connection or rolls back on error."""
    conn = psycopg2.connect(DB_URL)
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def fetch_one(
    query: str,
    params: Optional[tuple] = None,
    conn: Optional[psycopg2.extensions.connection] = None,
) -> Optional[dict]:
    """Execute *query* and return the first row as a dict, or None."""
    def _run(c: psycopg2.extensions.connection) -> Optional[dict]:
        with c.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(query, params)
            row = cur.fetchone()
            return dict(row) if row else None

    if conn is not None:
        return _run(conn)
    with get_connection() as c:
        return _run(c)


def fetch_all(
    query: str,
    params: Optional[tuple] = None,
    conn: Optional[psycopg2.extensions.connection] = None,
) -> list[dict]:
    """Execute *query* and return all rows as a list of dicts."""
    def _run(c: psycopg2.extensions.connection) -> list[dict]:
        with c.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(query, params)
            return [dict(r) for r in cur.fetchall()]

    if conn is not None:
        return _run(conn)
    with get_connection() as c:
        return _run(c)


def execute(
    query: str,
    params: Optional[tuple] = None,
    conn: Optional[psycopg2.extensions.connection] = None,
) -> Any:
    """Execute a write query. Returns cursor.rowcount."""
    def _run(c: psycopg2.extensions.connection) -> int:
        with c.cursor() as cur:
            cur.execute(query, params)
            return cur.rowcount

    if conn is not None:
        return _run(conn)
    with get_connection() as c:
        return _run(c)


def execute_returning(
    query: str,
    params: Optional[tuple] = None,
    conn: Optional[psycopg2.extensions.connection] = None,
) -> Optional[dict]:
    """Execute an INSERT/UPDATE ... RETURNING query and return the first row."""
    def _run(c: psycopg2.extensions.connection) -> Optional[dict]:
        with c.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(query, params)
            row = cur.fetchone()
            return dict(row) if row else None

    if conn is not None:
        return _run(conn)
    with get_connection() as c:
        return _run(c)

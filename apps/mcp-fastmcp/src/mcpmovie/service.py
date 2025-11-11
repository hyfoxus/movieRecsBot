from typing import List, Optional

from sqlalchemy import text

from mcpmovie.config import get_settings
from mcpmovie.database import session_scope
from mcpmovie.embeddings import embed_text
from mcpmovie.schemas import MovieContext, SearchRequest
from mcpmovie.database import SessionLocal
from sqlalchemy.orm import Session

settings = get_settings()


def _vector_literal(vector: list[float]) -> str:
    return "[" + ",".join(f"{x:.8f}" for x in vector) + "]"


async def search_movies(request: SearchRequest) -> List[MovieContext]:
    limit = min(settings.max_results, request.limit or settings.max_results)
    embedding = await embed_text(request.query)
    vec_literal = _vector_literal(embedding)

    sql = text(
        """
        WITH filtered AS (
            SELECT id, tconst, primary_title, start_year, rating, votes,
                   genres, plot, akas, directors, writers, principals,
                   episode, title_type, runtime_minutes, is_adult, embedding
            FROM movie m
            WHERE (:fromYear IS NULL OR m.start_year >= :fromYear)
              AND (:toYear   IS NULL OR m.start_year <= :toYear)
              AND (:runtimeMax IS NULL OR m.runtime_minutes <= :runtimeMax)
              AND (:minRating IS NULL OR m.rating >= :minRating)
              AND (
                    COALESCE(:incGenres, ARRAY[]::text[]) = ARRAY[]::text[]
                    OR EXISTS (SELECT 1 FROM unnest(:incGenres) g WHERE g = ANY(m.genres))
                  )
              AND (
                    COALESCE(:excGenres, ARRAY[]::text[]) = ARRAY[]::text[]
                    OR NOT EXISTS (SELECT 1 FROM unnest(:excGenres) g WHERE g = ANY(m.genres))
                  )
              AND m.embedding IS NOT NULL
        )
        SELECT tconst, primary_title, start_year, rating, votes, genres, plot,
               akas, directors, writers, principals, episode, title_type,
               runtime_minutes, is_adult,
               (1 - (embedding <=> CAST(:vec AS vector))) AS similarity
        FROM filtered
        ORDER BY similarity DESC
        LIMIT :limit
        """
    )

    params = {
        "fromYear": request.from_year,
        "toYear": request.to_year,
        "runtimeMax": request.runtime_minutes,
        "minRating": request.min_rating,
        "incGenres": request.include_genres or [],
        "excGenres": request.exclude_genres or [],
        "vec": vec_literal,
        "limit": limit,
    }

    with session_scope() as session:
        rows = session.execute(sql, params).mappings().all()

    contexts: List[MovieContext] = []
    for row in rows:
        metadata = {
            "plot": row.get("plot"),
            "akas": row.get("akas"),
            "directors": row.get("directors"),
            "writers": row.get("writers"),
            "principals": row.get("principals"),
            "episode": row.get("episode"),
            "titleType": row.get("title_type"),
            "runtimeMinutes": row.get("runtime_minutes"),
            "isAdult": row.get("is_adult"),
        }
        contexts.append(
            MovieContext(
                tconst=row["tconst"],
                title=row["primary_title"],
                year=row.get("start_year"),
                rating=row.get("rating"),
                votes=row.get("votes"),
                similarity=row["similarity"],
                genres=[g for g in (row.get("genres") or []) if g],
                metadata={k: v for k, v in metadata.items() if v is not None},
            )
        )
    return contexts


def fetch_movie(tconst: str) -> Optional[MovieContext]:
    sql = text(
        """
        SELECT tconst, primary_title, start_year, rating, votes, genres, plot,
               akas, directors, writers, principals, episode, title_type,
               runtime_minutes, is_adult
        FROM movie
        WHERE tconst = :tconst
        """
    )
    with session_scope() as session:
        row = session.execute(sql, {"tconst": tconst}).mappings().first()
        if not row:
            return None
    metadata = {
        "plot": row.get("plot"),
        "akas": row.get("akas"),
        "directors": row.get("directors"),
        "writers": row.get("writers"),
        "principals": row.get("principals"),
        "episode": row.get("episode"),
        "titleType": row.get("title_type"),
        "runtimeMinutes": row.get("runtime_minutes"),
        "isAdult": row.get("is_adult"),
    }
    return MovieContext(
        tconst=row["tconst"],
        title=row["primary_title"],
        year=row.get("start_year"),
        rating=row.get("rating"),
        votes=row.get("votes"),
        similarity=1.0,
        genres=[g for g in (row.get("genres") or []) if g],
        metadata={k: v for k, v in metadata.items() if v is not None},
    )

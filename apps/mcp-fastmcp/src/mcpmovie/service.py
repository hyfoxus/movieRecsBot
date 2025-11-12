from typing import List, Optional

from sqlalchemy import text, bindparam, Integer, Float, Text
from sqlalchemy.dialects.postgresql import ARRAY, TEXT

from mcpmovie.config import get_settings
from mcpmovie.database import session_scope
from mcpmovie.embeddings import embed_text
from mcpmovie.schemas import MovieContext, SearchRequest

settings = get_settings()


def _vector_literal(vector: list[float]) -> str:
    return "[" + ",".join(f"{x:.8f}" for x in vector) + "]"


async def search_movies(request: SearchRequest) -> List[MovieContext]:
    limit = min(settings.max_results, request.limit or settings.max_results)
    embedding = await embed_text(request.query)
    vec_literal = _vector_literal(embedding)

    where_clauses = ["m.embedding IS NOT NULL"]
    params = {"vec": vec_literal, "limit": limit}

    if request.from_year is not None:
        where_clauses.append("m.start_year >= :fromYear")
        params["fromYear"] = request.from_year
    if request.to_year is not None:
        where_clauses.append("m.start_year <= :toYear")
        params["toYear"] = request.to_year
    if request.runtime_minutes is not None:
        where_clauses.append("m.runtime_minutes <= :runtimeMax")
        params["runtimeMax"] = request.runtime_minutes
    if request.min_rating is not None:
        where_clauses.append("m.rating >= :minRating")
        params["minRating"] = request.min_rating
    inc_genres = request.include_genres or []
    if inc_genres:
        where_clauses.append(
            "EXISTS (SELECT 1 FROM unnest(:incGenres) g WHERE g = ANY(m.genres))"
        )
        params["incGenres"] = inc_genres
    exc_genres = request.exclude_genres or []
    if exc_genres:
        where_clauses.append(
            "NOT EXISTS (SELECT 1 FROM unnest(:excGenres) g WHERE g = ANY(m.genres))"
        )
        params["excGenres"] = exc_genres

    where_sql = " AND ".join(where_clauses)

    sql = text(
        f"""
        WITH filtered AS (
            SELECT id, tconst, primary_title, start_year, rating, votes,
                   genres, plot, title_type, runtime_minutes, is_adult, embedding
            FROM movie m
            WHERE {where_sql}
        )
        SELECT tconst, primary_title, start_year, rating, votes, genres, plot,
               title_type,
               runtime_minutes, is_adult,
               (1 - (embedding <=> CAST(:vec AS vector))) AS similarity
        FROM filtered
        ORDER BY similarity DESC
        LIMIT :limit
        """
    )

    with session_scope() as session:
        rows = session.execute(sql, params).mappings().all()

    contexts: List[MovieContext] = []
    for row in rows:
        metadata = {
            "plot": row.get("plot"),
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
               title_type,
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

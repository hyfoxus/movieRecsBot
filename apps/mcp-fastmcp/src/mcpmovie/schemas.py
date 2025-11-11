from typing import List, Optional

from pydantic import BaseModel, Field


class SearchRequest(BaseModel):
    query: str = Field(..., description="Natural language query for similar movies")
    include_genres: Optional[List[str]] = Field(default=None)
    exclude_genres: Optional[List[str]] = Field(default=None)
    from_year: Optional[int] = None
    to_year: Optional[int] = None
    runtime_minutes: Optional[int] = Field(
        default=None, description="Maximum runtime in minutes"
    )
    min_rating: Optional[float] = None
    limit: Optional[int] = Field(default=None, le=50)


class MovieContext(BaseModel):
    tconst: str
    title: str
    year: Optional[int]
    rating: Optional[float]
    votes: Optional[int]
    similarity: float
    genres: List[str] = Field(default_factory=list)
    metadata: dict = Field(default_factory=dict)


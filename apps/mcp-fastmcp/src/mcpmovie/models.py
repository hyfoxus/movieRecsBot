from sqlalchemy import Column, Integer, Float, Boolean, SmallInteger, String, text
from sqlalchemy.dialects.postgresql import ARRAY, JSONB
from sqlalchemy.orm import declarative_base

Base = declarative_base()


class Movie(Base):
    __tablename__ = "movie"

    id = Column(Integer, primary_key=True)
    tconst = Column(String, unique=True, nullable=False)
    primary_title = Column(String)
    original_title = Column(String)
    title_type = Column(String)
    is_adult = Column(Boolean)
    start_year = Column(SmallInteger)
    end_year = Column(SmallInteger)
    runtime_minutes = Column(SmallInteger)
    genres = Column(ARRAY(String))
    rating = Column(Float)
    votes = Column(Integer)
    plot = Column(String)
    akas = Column(JSONB)
    directors = Column(JSONB)
    writers = Column(JSONB)
    principals = Column(JSONB)
    episode = Column(JSONB)
    embedding_model = Column(String)

    similarity = Column(Float, nullable=True, default=None)  # populated via queries


from fastmcp import FastMCP
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
try:
    from fastmcp.responses import ToolResponse, ResourceResponse
except ImportError:  # fall back for older FastMCP builds
    try:
        from fastmcp.types import ToolResponse, ResourceResponse  # type: ignore
    except ImportError:
        from dataclasses import dataclass
        from typing import List, Dict, Any, Optional

        @dataclass
        class ToolResponse:  # type: ignore
            content: List[Dict[str, Any]]

        @dataclass
        class ResourceResponse:  # type: ignore
            content: List[Dict[str, Any]]
            uri: Optional[str] = None

from mcpmovie.config import get_settings
from mcpmovie.schemas import SearchRequest
from mcpmovie import service

settings = get_settings()

mcp = FastMCP(
    name=settings.server_name,
    version=settings.server_version,
)
setattr(mcp, "description", getattr(settings, "server_description", ""))  # backwards compat


async def _build_tool_payload(request: SearchRequest):
    movies = await service.search_movies(request)
    return {
        "content": [
            {
                "type": "text",
                "text": f"Top {len(movies)} matches for query '{request.query}'.",
            },
            {
                "type": "json",
                "json": [movie.dict() for movie in movies],
            },
        ]
    }


def _build_resource_payload(tconst: str):
    movie = service.fetch_movie(tconst)
    if movie is None:
        return {
            "uri": f"imdb://movie/{tconst}",
            "content": [{"type": "text", "text": f"Movie {tconst} not found."}],
        }
    return {
        "uri": f"imdb://movie/{tconst}",
        "content": [
            {"type": "text", "text": f"Metadata for {movie.title}"},
            {"type": "json", "json": movie.dict()},
        ],
    }


@mcp.tool(name="movie.search", description="Vector-based movie search over the IMDb subset.")
async def movie_search(request: SearchRequest):
    payload = await _build_tool_payload(request)
    if ToolResponse is not None:  # type: ignore
        return ToolResponse(**payload)  # type: ignore
    return payload


@mcp.resource("imdb://movie/{tconst}")
def movie_resource(tconst: str):
    payload = _build_resource_payload(tconst)
    if ResourceResponse is not None:  # type: ignore
        return ResourceResponse(**payload)  # type: ignore
    return payload


app = FastAPI(title=settings.server_name, version=settings.server_version)


class ToolInvocation(BaseModel):
    name: str
    arguments: dict = Field(default_factory=dict)


class ResourcePointer(BaseModel):
    uri: str


class ResourceQuery(BaseModel):
    resources: list[ResourcePointer]


@app.get("/.well-known/mcp.json")
def manifest():
    return {
        "name": settings.server_name,
        "version": settings.server_version,
        "description": settings.server_description,
        "tools": [
            {
                "name": "movie.search",
                "description": "Vector-based movie search over the IMDb subset.",
                "input_schema": SearchRequest.model_json_schema(),
            }
        ],
        "resources": [
            {
                "uri": "imdb://movie/{tconst}",
                "name": "Movie metadata",
                "description": "Returns metadata for a specific IMDb title.",
            }
        ],
    }


@app.post("/mcp/v1/tools")
async def invoke_tool(req: ToolInvocation):
    if req.name != "movie.search":
        raise HTTPException(status_code=404, detail="Tool not found")
    payload = await _build_tool_payload(SearchRequest(**req.arguments))
    return payload


@app.post("/mcp/v1/resources/query")
def query_resources(query: ResourceQuery):
    results = []
    for pointer in query.resources:
        if not pointer.uri.startswith("imdb://movie/"):
            results.append(
                {
                    "uri": pointer.uri,
                    "content": [{"type": "text", "text": "Unsupported resource"}],
                }
            )
            continue
        tconst = pointer.uri.split("/")[-1]
        results.append(_build_resource_payload(tconst))
    return {"results": results}


def run():
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8082)


if __name__ == "__main__":
    run()

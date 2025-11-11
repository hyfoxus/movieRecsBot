import httpx

from mcpmovie.config import get_settings

settings = get_settings()


async def embed_text(text: str) -> list[float]:
    payload = {
        "model": settings.ollama_embed_model,
        "prompt": text or "",
        "stream": False,
    }
    async with httpx.AsyncClient(base_url=settings.ollama_base_url, timeout=30) as client:
        resp = await client.post("/api/embeddings", json=payload)
        resp.raise_for_status()
        data = resp.json()
        embedding = data.get("embedding")
        if embedding is None:
            raise ValueError("Ollama embeddings API response missing 'embedding'")
        return [float(x) for x in embedding]

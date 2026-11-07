from fastapi import APIRouter

from greedycat.api.models import Health

router = APIRouter()


@router.get("/health", response_model=Health)
async def health() -> Health:
    """Service liveness only; never starts or waits for ADB."""
    return Health()

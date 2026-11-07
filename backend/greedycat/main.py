"""Local diagnostic service. Launch with `python -m greedycat.server`."""

from ipaddress import ip_address
from urllib.parse import urlsplit

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException

from greedycat.api.health import router
from greedycat.api.models import Discovery, ServiceError
from greedycat.devices.adb import DeviceDiscovery


def _local_authority(authority: str) -> bool:
    try:
        parsed = urlsplit("http://" + authority)
        return (parsed.hostname in {"127.0.0.1", "localhost", "::1"}
                and parsed.username is None and parsed.password is None
                and not parsed.path and not parsed.query and not parsed.fragment
                and (parsed.port is None or 1 <= parsed.port <= 65535))
    except ValueError:
        return False


def _error(code: str, message: str, status: int) -> JSONResponse:
    error = ServiceError(code=code, path="", message=message)
    return JSONResponse(error.model_dump(), status_code=status, headers={"Cache-Control": "no-store"})


def create_app(discovery: DeviceDiscovery | None = None) -> FastAPI:
    probe = discovery if discovery is not None else DeviceDiscovery()
    app = FastAPI(title="Greedy Cat Local Diagnostics", version="0.3.0",
                  openapi_url="/api/v1/openapi.json", docs_url=None, redoc_url=None,
                  responses={status: {"model": ServiceError} for status in (403, 404, 405, 422, 500)})

    @app.middleware("http")
    async def local_access(request: Request, call_next):
        client = request.client
        try:
            local = client is not None and ip_address(client.host).is_loopback
        except ValueError:
            local = False
        if not local:
            return _error("LOCAL_ONLY", "This service accepts loopback clients only.", 403)
        hosts = request.headers.getlist("host")
        if len(hosts) != 1 or not _local_authority(hosts[0]):
            return _error("HOST_DENIED", "A local Host header is required.", 403)
        origins = request.headers.getlist("origin")
        # Same-origin browser use only. T004 should use a dev-server proxy.
        if origins and (len(origins) != 1 or origins[0] != f"http://{hosts[0]}"):
            return _error("ORIGIN_DENIED", "Cross-origin requests are not enabled.", 403)
        if request.headers.get("sec-fetch-site") == "cross-site":
            return _error("ORIGIN_DENIED", "Cross-site browser requests are not enabled.", 403)
        response = await call_next(request)
        response.headers["Cache-Control"] = "no-store"
        response.headers["X-Content-Type-Options"] = "nosniff"
        return response

    app.include_router(router, prefix="/api/v1")

    @app.get("/api/v1/devices", response_model=Discovery)
    def devices() -> Discovery:
        # FastAPI dispatches synchronous work off the event loop.
        return probe.discover()

    @app.exception_handler(HTTPException)
    async def http_error(request: Request, error: HTTPException):
        code = "NOT_FOUND" if error.status_code == 404 else "METHOD_NOT_ALLOWED" if error.status_code == 405 else "INVALID_REQUEST"
        return _error(code, "The requested resource or method is unavailable.", error.status_code)

    @app.exception_handler(RequestValidationError)
    async def validation_error(request: Request, error: RequestValidationError):
        return _error("INVALID_REQUEST", "Request does not satisfy the API contract.", 422)

    @app.exception_handler(Exception)
    async def internal_error(request: Request, error: Exception):
        return _error("INTERNAL_ERROR", "Local service encountered an unexpected error.", 500)

    return app


app = create_app()

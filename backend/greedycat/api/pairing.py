"""Authenticated native HTTPS pairing and human-review sync; no device control."""

from ipaddress import ip_address
import re
from typing import Literal
from uuid import UUID
from urllib.parse import urlsplit

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field
from starlette.exceptions import HTTPException

from greedycat.main import _local_authority
from greedycat.security.pairing import PairingDenied, PairingStore
from greedycat.security.addresses import private_address
from greedycat.sync.ingest import SyncStore, SyncConflict

SECRET = re.compile(r"[A-Za-z0-9_-]{43}\Z")


class Exchange(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    invitation: str = Field(pattern=r"^[A-Za-z0-9_-]{43}$", min_length=43, max_length=43)


class Session(BaseModel):
    schema_version: Literal["pairing-1"] = "pairing-1"
    pairing_id: UUID
    issued_at: float
    expires_at: float
    status: Literal["PAIRED", "REVOKED"]


class Credential(BaseModel):
    schema_version: Literal["pairing-1"] = "pairing-1"
    pairing_id: UUID
    issued_at: float
    expires_at: float
    token: str


class ReviewEnvelope(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    schema_version: Literal["sync-1"]
    source_id: str = Field(pattern=r"^[a-f0-9]{8}-[a-f0-9]{4}-[1-5][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$")
    event_id: str = Field(pattern=r"^[a-f0-9]{8}-[a-f0-9]{4}-[1-5][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$")
    payload_sha256: str = Field(pattern=r"^[a-f0-9]{64}$")
    payload_json: str = Field(max_length=8192)


def create_pairing_app(store: PairingStore, bind: str = "127.0.0.1") -> FastAPI:
    private_address(bind)
    lan = not ip_address(bind).is_loopback
    research = SyncStore(store.path.parent / "research.sqlite3")
    app = FastAPI(title="Greedy Cat pairing", version="pairing-1", docs_url=None, redoc_url=None,
                  openapi_url=None)

    def error(status: int, code: str):
        return JSONResponse({"schema_version": "pairing-1", "code": code}, status_code=status,
                            headers={"Cache-Control": "no-store", "X-Content-Type-Options": "nosniff"})

    @app.middleware("http")
    async def boundary(request: Request, call_next):
        try:
            local = request.client is not None and (ip_address(request.client.host).is_loopback or
                lan and bool(private_address(request.client.host)))
        except ValueError:
            local = False
        if not local:
            return error(403, "LOCAL_ONLY")
        hosts = request.headers.getlist("host")
        def authority(value):
            if not lan and bind == "127.0.0.1":
                return _local_authority(value)
            try:
                url = urlsplit("https://" + value)
                return (url.hostname == bind and url.username is None and url.password is None and
                        not url.path and not url.query and not url.fragment and (url.port is None or 1 <= url.port <= 65535))
            except ValueError:
                return False
        if len(hosts) != 1 or not authority(hosts[0]):
            return error(403, "HOST_DENIED")
        if request.url.scheme != "https":
            return error(403, "TLS_REQUIRED")
        # This API is for native clients only, including local operator tooling.
        if "origin" in request.headers or "sec-fetch-site" in request.headers:
            return error(403, "BROWSER_DENIED")
        size = 0
        chunks = []
        async for chunk in request.stream():
            size += len(chunk)
            if size > (16384 if request.url.path == "/api/sync/v1/reviews" else 4096):
                return error(413, "BODY_LIMIT")
            chunks.append(chunk)
        # Cache only the bounded body for the downstream JSON parser.
        request._body = b"".join(chunks)
        response = await call_next(request)
        response.headers["Cache-Control"] = "no-store"
        response.headers["X-Content-Type-Options"] = "nosniff"
        return response

    def token(request: Request) -> str:
        values = request.headers.getlist("authorization")
        if len(values) != 1 or not values[0].startswith("Bearer ") or not SECRET.fullmatch(values[0][7:]):
            raise PairingDenied()
        return values[0][7:]

    @app.post("/api/pairing/v1/exchange", response_model=Credential)
    def exchange(body: Exchange):
        return store.exchange(body.invitation)

    @app.get("/api/pairing/v1/session", response_model=Session)
    def session(request: Request):
        return store.authenticate(token(request))

    @app.delete("/api/pairing/v1/session", response_model=Session)
    def revoke(request: Request):
        return store.authenticate(token(request), revoke=True)

    @app.post("/api/sync/v1/reviews")
    def ingest(request: Request, body: ReviewEnvelope):
        owner = store.authenticate(token(request))["pairing_id"]
        try:
            return research.ingest(owner, body.source_id, body.event_id, body.payload_sha256, body.payload_json)
        except SyncConflict as denied:
            return JSONResponse(dict(schema_version="sync-1", event_id=body.event_id,
                payload_sha256=body.payload_sha256, status="CONFLICT", code=denied.code,
                retryable=denied.retryable), status_code=409)
        except ValueError:
            return JSONResponse(dict(schema_version="sync-1", event_id=body.event_id,
                payload_sha256=body.payload_sha256, status="CONFLICT", code="INVALID_REVIEW", retryable=False), status_code=422)

    @app.exception_handler(PairingDenied)
    async def denied(request, exception):
        return error(401, "PAIRING_DENIED")

    @app.exception_handler(RequestValidationError)
    async def invalid(request, exception):
        return error(422, "INVALID_REQUEST")

    @app.exception_handler(HTTPException)
    async def http_error(request, exception):
        return error(exception.status_code, "UNAVAILABLE")

    @app.exception_handler(Exception)
    async def internal_error(request, exception):
        return error(500, "PAIRING_UNAVAILABLE")

    return app

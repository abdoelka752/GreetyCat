"""Typed diagnostics, separate from persisted gameplay contracts."""

from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class ResponseModel(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)


class Health(ResponseModel):
    schema_version: Literal["1.0"] = "1.0"
    service: Literal["greedycat-local"] = "greedycat-local"
    status: Literal["OK"] = "OK"
    api_version: Literal["v1"] = "v1"
    gameplay_contract_version: Literal["1.0"] = "1.0"
    access_mode: Literal["LOOPBACK_ONLY"] = "LOOPBACK_ONLY"
    pairing: Literal["NOT_IMPLEMENTED"] = "NOT_IMPLEMENTED"


class Device(ResponseModel):
    device_id: UUID
    state: Literal["AVAILABLE", "UNAUTHORIZED", "OFFLINE", "UNKNOWN"]
    authorized: bool | None
    # ADB transport visibility proves none of these capabilities.
    screen_available: Literal["UNKNOWN"] = "UNKNOWN"
    game_visible: Literal["UNKNOWN"] = "UNKNOWN"
    capture_active: Literal["UNKNOWN"] = "UNKNOWN"
    analyzer_active: Literal["UNKNOWN"] = "UNKNOWN"


class Discovery(ResponseModel):
    schema_version: Literal["1.0"] = "1.0"
    status: Literal["OK", "ADB_MISSING", "ADB_ERROR", "TIMEOUT", "OUTPUT_LIMIT", "INVALID_OUTPUT", "BUSY"]
    devices: list[Device] = Field(default_factory=list, max_length=64)
    observed_at: str
    duration_ms: float = Field(ge=0)


class ServiceError(ResponseModel):
    schema_version: Literal["1.0"] = "1.0"
    code: Literal["LOCAL_ONLY", "HOST_DENIED", "ORIGIN_DENIED", "NOT_FOUND", "METHOD_NOT_ALLOWED", "INVALID_REQUEST", "INTERNAL_ERROR"]
    path: str
    message: str
    retryable: Literal[False] = False

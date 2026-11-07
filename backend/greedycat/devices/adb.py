"""Bounded `adb devices -l` only. No device commands or raw identifier output."""

from datetime import datetime, timezone
import hashlib
import hmac
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import tempfile
import threading
import time
from uuid import UUID

from greedycat.api.models import Device, Discovery

MAX_OUTPUT = 65536
MAX_DEVICES = 64


class ProbeFailure(Exception):
    def __init__(self, status: str):
        self.status = status
        super().__init__(status)


def resolve_adb(explicit: str | None = None) -> str | None:
    """Explicit trusted local file, then PATH, then configured Android SDK."""
    executable = "adb.exe" if os.name == "nt" else "adb"
    if explicit is not None:
        path = Path(explicit).expanduser()
        return str(path.resolve()) if path.is_file() else None
    found = shutil.which("adb")
    if found:
        return found
    for variable in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if root := os.environ.get(variable):
            path = Path(root) / "platform-tools" / executable
            if path.is_file():
                return str(path.resolve())
    return None


def run_adb(executable: str, timeout: float) -> bytes:
    """Single client process; bounded capture and timeout, no console on Windows."""
    environment = os.environ.copy()
    for key in ("ADB_SERVER_SOCKET", "ANDROID_ADB_SERVER_ADDRESS", "ANDROID_ADB_SERVER_PORT"):
        environment.pop(key, None)
    command = [executable, "-H", "127.0.0.1", "-P", "5037", "devices", "-l"]
    flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
    # A file avoids pipe deadlocks if the ADB daemon inherits client handles.
    # Limit retained bytes; poll file size to terminate excess output promptly.
    with tempfile.TemporaryFile() as output:
        with subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=output,
                              stderr=output, shell=False, env=environment,
                              creationflags=flags) as process:
            deadline = time.monotonic() + timeout
            failure = None
            while process.poll() is None:
                if os.fstat(output.fileno()).st_size > MAX_OUTPUT:
                    failure = "OUTPUT_LIMIT"
                    break
                if time.monotonic() >= deadline:
                    failure = "TIMEOUT"
                    break
                time.sleep(0.02)
            if failure:
                process.kill()
                process.wait()
                raise ProbeFailure(failure)
            output.seek(0)
            data = output.read(MAX_OUTPUT + 1)
            if len(data) > MAX_OUTPUT:
                raise ProbeFailure("OUTPUT_LIMIT")
            if process.returncode:
                raise ProbeFailure("ADB_ERROR")
            return data


def parse_devices(data: bytes, key: bytes) -> list[Device]:
    if len(data) > MAX_OUTPUT:
        raise ProbeFailure("OUTPUT_LIMIT")
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError:
        raise ProbeFailure("INVALID_OUTPUT") from None
    header_seen = False
    serials = set()
    devices = []
    for raw in text.splitlines():
        line = raw.strip()
        if not line:
            continue
        if not header_seen:
            if line == "List of devices attached":
                header_seen = True
            elif line.startswith("* daemon "):
                continue
            else:
                raise ProbeFailure("INVALID_OUTPUT")
            continue
        parts = line.split()
        if len(parts) < 2 or not re.fullmatch(r"[A-Za-z0-9_.:\[\]%-]{1,256}", parts[0]):
            raise ProbeFailure("INVALID_OUTPUT")
        serial, raw_state = parts[:2]
        if serial in serials or len(devices) >= MAX_DEVICES:
            raise ProbeFailure("INVALID_OUTPUT")
        serials.add(serial)
        state = {"device": "AVAILABLE", "unauthorized": "UNAUTHORIZED", "offline": "OFFLINE"}.get(raw_state, "UNKNOWN")
        # Key is random per server session, so IDs cannot track hardware across restarts.
        digest = hmac.new(key, serial.encode("utf-8"), hashlib.sha256).digest()
        devices.append(Device(device_id=UUID(bytes=digest[:16], version=4), state=state,
                              authorized=True if state == "AVAILABLE" else False if state == "UNAUTHORIZED" else None))
    if not header_seen:
        raise ProbeFailure("INVALID_OUTPUT")
    return devices


class DeviceDiscovery:
    """At most one bounded diagnostic subprocess; concurrent calls return BUSY."""

    def __init__(self, adb_path: str | None = None, timeout: float = 5.0):
        if not 0 < timeout <= 10:
            raise ValueError("ADB timeout must be greater than zero and at most 10 seconds")
        self.adb_path, self.timeout = adb_path, timeout
        self._key = secrets.token_bytes(32)
        self._lock = threading.Lock()

    def discover(self) -> Discovery:
        start = time.monotonic()
        if not self._lock.acquire(blocking=False):
            return self._result("BUSY", [], start)
        try:
            try:
                executable = resolve_adb(self.adb_path)
                if executable is None:
                    return self._result("ADB_MISSING", [], start)
                devices = parse_devices(run_adb(executable, self.timeout), self._key)
            except ProbeFailure as error:
                return self._result(error.status, [], start)
            except OSError:
                return self._result("ADB_ERROR", [], start)
            return self._result("OK", devices, start)
        finally:
            self._lock.release()

    @staticmethod
    def _result(status: str, devices: list[Device], start: float) -> Discovery:
        return Discovery(status=status, devices=devices,
                         observed_at=datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z"),
                         duration_ms=max(0.0, (time.monotonic() - start) * 1000))

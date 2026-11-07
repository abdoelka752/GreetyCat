from concurrent.futures import ThreadPoolExecutor
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from unittest.mock import patch

from greedycat.devices.adb import (
    DeviceDiscovery, MAX_OUTPUT, ProbeFailure, parse_devices, resolve_adb, run_adb,
)

SYNTHETIC_OUTPUT = b"""List of devices attached
synthetic-a device product:fixture model:Fake transport_id:1
synthetic-b unauthorized transport_id:2
synthetic-c offline
synthetic-d recovery
synthetic-e no permissions (test fixture)
"""


class DeviceTests(unittest.TestCase):
    def test_parse_states_and_never_infer_capture_or_visibility(self):
        devices = parse_devices(SYNTHETIC_OUTPUT, b"test-key")
        self.assertEqual(["AVAILABLE", "UNAUTHORIZED", "OFFLINE", "UNKNOWN", "UNKNOWN"],
                         [device.state for device in devices])
        self.assertEqual([True, False, None, None, None], [device.authorized for device in devices])
        for device in devices:
            for field in ("screen_available", "game_visible", "capture_active", "analyzer_active"):
                self.assertEqual("UNKNOWN", getattr(device, field))
            self.assertNotIn("synthetic-", device.model_dump_json())
            self.assertNotIn("Fake", device.model_dump_json())

    def test_pseudonyms_stable_within_session_and_change_with_key(self):
        first = parse_devices(SYNTHETIC_OUTPUT, b"test-key")
        self.assertEqual(first, parse_devices(SYNTHETIC_OUTPUT, b"test-key"))
        second = parse_devices(SYNTHETIC_OUTPUT, b"other-test-key")
        self.assertNotEqual(first[0].device_id, second[0].device_id)
        self.assertEqual(len(first), len({device.device_id for device in first}))

    def test_empty_list_and_daemon_start_banners(self):
        self.assertEqual([], parse_devices(b"List of devices attached\r\n\r\n", b"key"))
        banner = b"* daemon not running; starting now at tcp:5037\n* daemon started successfully\n"
        self.assertEqual(5, len(parse_devices(banner + SYNTHETIC_OUTPUT, b"key")))

    def test_invalid_output_is_not_a_successful_empty_list(self):
        for data in (b"", b"error: access denied", b"\xff", b"List of devices attached\ntruncated",
                     b"List of devices attached\na device\na offline", b"not a header\na device"):
            with self.subTest(data=data), self.assertRaises(ProbeFailure) as caught:
                parse_devices(data, b"key")
            self.assertEqual("INVALID_OUTPUT", caught.exception.status)

    def test_output_and_device_count_limits(self):
        with self.assertRaises(ProbeFailure) as caught:
            parse_devices(b"x" * (MAX_OUTPUT + 1), b"key")
        self.assertEqual("OUTPUT_LIMIT", caught.exception.status)
        data = b"List of devices attached\n" + b"".join(f"fake-{i} device\n".encode() for i in range(65))
        with self.assertRaises(ProbeFailure) as caught:
            parse_devices(data, b"key")
        self.assertEqual("INVALID_OUTPUT", caught.exception.status)

    def test_resolve_path_and_configured_sdk_without_executing_anything(self):
        with tempfile.TemporaryDirectory() as directory:
            executable = Path(directory) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
            executable.parent.mkdir()
            executable.touch()
            self.assertEqual(str(executable.resolve()), resolve_adb(str(executable)))
            self.assertIsNone(resolve_adb(str(executable.with_name("missing"))))
            with patch("shutil.which", return_value=None), patch.dict(os.environ, {"ANDROID_HOME": directory}, clear=True):
                self.assertEqual(str(executable.resolve()), resolve_adb())
            with patch("shutil.which", return_value=None), patch.dict(os.environ, {}, clear=True):
                self.assertIsNone(resolve_adb())
        with patch("shutil.which", return_value="trusted-adb"):
            self.assertEqual("trusted-adb", resolve_adb())

    def test_discovery_missing_adb_does_not_launch_process(self):
        with patch("greedycat.devices.adb.resolve_adb", return_value=None), patch("greedycat.devices.adb.run_adb") as runner:
            result = DeviceDiscovery().discover()
        self.assertEqual("ADB_MISSING", result.status)
        self.assertEqual([], result.devices)
        runner.assert_not_called()

    def test_discovery_failure_states_release_lock_and_do_not_echo_errors(self):
        for error, status in ((ProbeFailure("TIMEOUT"), "TIMEOUT"),
                              (ProbeFailure("OUTPUT_LIMIT"), "OUTPUT_LIMIT"),
                              (PermissionError("private path"), "ADB_ERROR")):
            with self.subTest(status=status), patch("greedycat.devices.adb.resolve_adb", return_value="adb"), \
                    patch("greedycat.devices.adb.run_adb", side_effect=[error, SYNTHETIC_OUTPUT]):
                probe = DeviceDiscovery()
                result = probe.discover()
                self.assertEqual(status, result.status)
                self.assertNotIn("private path", result.model_dump_json())
                self.assertEqual("OK", probe.discover().status)

    def test_resolution_permission_error_is_a_diagnostic_failure(self):
        with patch("greedycat.devices.adb.resolve_adb", side_effect=PermissionError):
            self.assertEqual("ADB_ERROR", DeviceDiscovery().discover().status)

    def test_concurrent_discovery_returns_busy_without_another_process(self):
        entered, release = threading.Event(), threading.Event()

        def runner(*args):
            entered.set()
            if not release.wait(3):
                raise AssertionError("Test did not release discovery")
            return SYNTHETIC_OUTPUT

        with patch("greedycat.devices.adb.resolve_adb", return_value="adb"), \
                patch("greedycat.devices.adb.run_adb", side_effect=runner) as mocked, \
                ThreadPoolExecutor(max_workers=1) as executor:
            probe = DeviceDiscovery()
            future = executor.submit(probe.discover)
            try:
                self.assertTrue(entered.wait(2))
                self.assertEqual("BUSY", probe.discover().status)
            finally:
                release.set()
            self.assertEqual("OK", future.result(timeout=3).status)
            self.assertEqual(1, mocked.call_count)

    def test_timeout_configuration_is_bounded(self):
        for timeout in (0, -1, 11, float("nan"), float("inf")):
            with self.subTest(timeout=timeout), self.assertRaises(ValueError):
                DeviceDiscovery(timeout=timeout)

    def run_synthetic_process(self, source, timeout=2):
        real_popen = subprocess.Popen
        children = []
        with tempfile.TemporaryDirectory() as directory:
            script = Path(directory) / "synthetic_adb.py"
            script.write_text(source, encoding="utf-8")

            def launch(command, **kwargs):
                self.assertEqual(["trusted-adb", "-H", "127.0.0.1", "-P", "5037", "devices", "-l"], command)
                self.assertFalse(kwargs["shell"])
                for variable in ("ADB_SERVER_SOCKET", "ANDROID_ADB_SERVER_ADDRESS", "ANDROID_ADB_SERVER_PORT"):
                    self.assertNotIn(variable, kwargs["env"])
                child = real_popen([sys.executable, str(script)], **kwargs)
                children.append(child)
                return child

            with patch("greedycat.devices.adb.subprocess.Popen", side_effect=launch), \
                    patch.dict(os.environ, {"ADB_SERVER_SOCKET": "tcp:remote:1234", "ANDROID_ADB_SERVER_PORT": "9999"}):
                try:
                    return run_adb("trusted-adb", timeout)
                finally:
                    self.assertTrue(children)
                    self.assertIsNotNone(children[0].poll(), "Diagnostic child must be reaped")

    def test_real_subprocess_runner_returns_only_bounded_readonly_query_output(self):
        data = self.run_synthetic_process("print('List of devices attached')")
        self.assertEqual([], parse_devices(data, b"key"))

    def test_real_subprocess_runner_times_out_and_reaps_child(self):
        start = time.monotonic()
        with self.assertRaises(ProbeFailure) as caught:
            self.run_synthetic_process("import time; time.sleep(30)", timeout=0.1)
        self.assertEqual("TIMEOUT", caught.exception.status)
        self.assertLess(time.monotonic() - start, 5)

    def test_real_subprocess_runner_limits_output_and_handles_nonzero_exit(self):
        for source, status in ((f"import sys; sys.stdout.write('x' * {MAX_OUTPUT + 1024})", "OUTPUT_LIMIT"),
                               ("import sys; sys.stderr.write('synthetic failure'); sys.exit(1)", "ADB_ERROR")):
            with self.subTest(status=status), self.assertRaises(ProbeFailure) as caught:
                self.run_synthetic_process(source)
            self.assertEqual(status, caught.exception.status)


if __name__ == "__main__":
    unittest.main()

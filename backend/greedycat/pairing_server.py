"""Explicit operator pairing CLI; loopback by default, opt-in private-IP TLS only."""

import argparse
import json
from pathlib import Path
import sqlite3

import uvicorn

from greedycat.api.pairing import create_pairing_app
from greedycat.security.identity import fingerprint, initialize, server_context, validate_bind
from greedycat.security.pairing import PairingDenied, PairingStore
from greedycat.security.addresses import private_address


DEFAULT_DIRECTORY = Path(__file__).resolve().parents[2] / "data" / "pairing"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, default=DEFAULT_DIRECTORY)
    sub = parser.add_subparsers(dest="command", required=True)
    init = sub.add_parser("init", help="Create a private TLS identity in an empty directory.")
    init.add_argument("--address", default="127.0.0.1", help="Optional private IP to include in the certificate.")
    sub.add_parser("invite", help="Replace the single 120-second, one-use invitation.")
    sub.add_parser("list", help="List active pseudonymous pairing IDs, never credentials.")
    sub.add_parser("cancel", help="Cancel the outstanding invitation.")
    revoke = sub.add_parser("revoke")
    revoke.add_argument("pairing_id")
    serve = sub.add_parser("serve")
    serve.add_argument("--port", type=int, default=8767)
    serve.add_argument("--bind", default="127.0.0.1")
    serve.add_argument("--allow-lan", action="store_true", help="Explicitly permit a certificate-matched RFC1918 bind.")
    args = parser.parse_args()
    try:
        if args.command == "init":
            initialize(args.directory, args.address)
            print(json.dumps({"certificate_sha256": fingerprint(args.directory)}))
            return
        # Fail before creating a DB when identity is missing, invalid or mismatched.
        context = server_context(args.directory)
        store = PairingStore(args.directory / "pairing.sqlite3")
        if args.command == "invite":
            print(json.dumps({**store.invite(), "certificate_sha256": fingerprint(args.directory)}))
        elif args.command == "list":
            print(json.dumps(store.list_sessions()))
        elif args.command == "cancel":
            store.cancel_invitation()
            print(json.dumps({"invitation": "CANCELED"}))
        elif args.command == "revoke":
            print(json.dumps({"revoked": store.revoke(args.pairing_id)}))
        else:
            if not 1 <= args.port <= 65535:
                parser.error("port must be between 1 and 65535")
            bind = private_address(args.bind)
            if not bind.startswith("127.") and not args.allow_lan:
                parser.error("private-IP binding requires explicit --allow-lan")
            validate_bind(args.directory, bind)
            # Build the supported Uvicorn SSL config, then enforce TLS >= 1.2.
            config = uvicorn.Config(create_pairing_app(store, bind), host=bind, port=args.port,
                                    workers=1, proxy_headers=False, access_log=False, server_header=False,
                                    ssl_certfile=str(args.directory / "server.pem"),
                                    ssl_keyfile=str(args.directory / "server.key"),
                                    limit_concurrency=16, timeout_keep_alive=5, ws="none")
            config.load()
            config.ssl = context
            uvicorn.Server(config).run()
    except (OSError, ValueError, sqlite3.Error, PairingDenied):
        parser.exit(1, "Pairing operation failed; check private identity, database and PC clock.\n")


if __name__ == "__main__":
    main()

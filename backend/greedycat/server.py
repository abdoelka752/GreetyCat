"""Fixed loopback launcher; no host override until authenticated LAN support."""

import argparse

import uvicorn


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    if not 1 <= args.port <= 65535:
        parser.error("port must be between 1 and 65535")
    uvicorn.run("greedycat.main:app", host="127.0.0.1", port=args.port, workers=1,
                proxy_headers=False, access_log=False, server_header=False)


if __name__ == "__main__":
    main()

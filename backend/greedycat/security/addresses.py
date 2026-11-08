"""Explicit numeric IPv4 endpoints; never use wildcard/public binds or DNS."""

from ipaddress import IPv4Address, IPv4Network

PRIVATE = tuple(IPv4Network(value) for value in ("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8"))


def private_address(value: str) -> str:
    address = IPv4Address(value)
    if not any(address in network for network in PRIVATE):
        raise ValueError("Only loopback or RFC1918 IPv4 addresses are supported.")
    return str(address)

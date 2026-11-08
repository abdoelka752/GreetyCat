"""Explicit local TLS identity generation. Never silently replace an existing key."""

from datetime import datetime, timedelta, timezone
import hashlib
from ipaddress import ip_address
from pathlib import Path
import ssl

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.x509.oid import ExtendedKeyUsageOID, NameOID
from greedycat.security.addresses import private_address


def initialize(directory: Path, address: str = "127.0.0.1") -> None:
    address = private_address(address)
    directory.mkdir(parents=True, exist_ok=True)
    if any(directory.iterdir()):
        raise ValueError("Identity directory must be empty; existing identity was preserved.")
    key = ec.generate_private_key(ec.SECP256R1())
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "Greedy Cat local pairing")])
    now = datetime.now(timezone.utc)
    cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(key.public_key())
            .serial_number(x509.random_serial_number()).not_valid_before(now - timedelta(minutes=5))
            .not_valid_after(now + timedelta(days=365))
            .add_extension(x509.SubjectAlternativeName([x509.IPAddress(ip_address(value)) for value in sorted({"127.0.0.1", address})]), critical=False)
            .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
            .add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH]), critical=False)
            .sign(key, hashes.SHA256()))
    with (directory / "server.key").open("xb") as output:
        output.write(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                      serialization.NoEncryption()))
    with (directory / "server.pem").open("xb") as output:
        output.write(cert.public_bytes(serialization.Encoding.PEM))


def fingerprint(directory: Path) -> str:
    cert = x509.load_pem_x509_certificate((directory / "server.pem").read_bytes())
    return hashlib.sha256(cert.public_bytes(serialization.Encoding.DER)).hexdigest()


def server_context(directory: Path) -> ssl.SSLContext:
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.load_cert_chain(directory / "server.pem", directory / "server.key")
    return context


def validate_bind(directory: Path, address: str) -> None:
    cert = x509.load_pem_x509_certificate((directory / "server.pem").read_bytes())
    now = datetime.now(timezone.utc)
    if not cert.not_valid_before_utc <= now < cert.not_valid_after_utc:
        raise ValueError("TLS identity is expired or not yet valid.")
    if ip_address(private_address(address)) not in cert.extensions.get_extension_for_class(x509.SubjectAlternativeName).value.get_values_for_type(x509.IPAddress):
        raise ValueError("Bind address does not match the TLS identity.")

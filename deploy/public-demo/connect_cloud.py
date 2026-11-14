"""Start the cloud Compose bundle and connect it to the existing fixed demo URL."""

import argparse
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import time
import urllib.error
import urllib.request


ROOT = Path(__file__).resolve().parent
HOST = "skincare-ai.skincare-server.workers.dev"
ORIGIN = "https://" + HOST
PRIVATE = ROOT / "private"
CONNECTOR_PROFILE = "cloud-connector"
TUNNEL_URL = re.compile(r"https://[a-z0-9-]+\.trycloudflare\.com")


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def request(origin, path, headers=None, payload=None):
    headers = {"User-Agent": "SkinCare-AI-Cloud-Deployment/1.0", **(headers or {})}
    body = None
    if payload is not None:
        headers["Content-Type"] = "application/json"
        body = json.dumps(payload).encode()
    req = urllib.request.Request(origin + path, data=body, headers=headers)
    try:
        with urllib.request.build_opener(NoRedirect).open(req, timeout=20) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"{path} returned HTTP {error.code}") from None


def wait_for(check, seconds, message):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            result = check()
            if result:
                return result
        except (RuntimeError, urllib.error.URLError, TimeoutError, ValueError):
            pass
        time.sleep(3)
    raise RuntimeError(message)


def compose_command(env_file, llm, cpu=False):
    command = [
        "docker", "compose", "--env-file", env_file, "-f", "compose.yaml",
        "-f", "compose.cloud.yaml", "--profile", CONNECTOR_PROFILE,
    ]
    if llm or cpu:
        command += ["-f", "compose.llm.yaml"]
    if cpu:
        # GPU-less hosts such as Oracle Cloud Always Free (Ampere A1).
        command += ["-f", "compose.llm-cpu.yaml"]
    return command


def public_route_healthy():
    """True when the fixed address already reaches a working backend."""
    try:
        request(ORIGIN, "/__skincare/health")
        request(ORIGIN, "/api/wc/auth/social/providers")
        return True
    except (RuntimeError, urllib.error.URLError, TimeoutError, ValueError, OSError):
        return False


def verify_compose(command):
    result = subprocess.run(command + ["config", "--format", "json"], cwd=ROOT,
                            capture_output=True, check=True)
    services = json.loads(result.stdout)["services"]
    if any(service.get("ports") for service in services.values()):
        raise RuntimeError("Cloud configuration must publish zero container ports")
    environment = services["backend"]["environment"]
    for name in ("SOCIAL_PUBLIC_BASE_URL", "SKINCARE_WEB_ALLOWED_ORIGINS"):
        if environment.get(name) != ORIGIN:
            raise RuntimeError(f"{name} must use {ORIGIN}")
    if environment.get("SPRING_JPA_HIBERNATE_DDL_AUTO") != "validate":
        raise RuntimeError("Cloud backend must validate the migrated database schema")


def gateway_config():
    keys = json.loads((PRIVATE / "gateway-secrets.json").read_text(encoding="utf-8-sig"))
    for name in ("CONNECT_TOKEN", "GATEWAY_TOKEN"):
        if not re.fullmatch(r"[0-9a-f]{64}", keys.get(name, "")):
            raise RuntimeError(f"Missing or invalid {name}")
    connection = secrets.token_hex(16)
    payload = json.dumps({"service": "skincare-gateway", "connection": connection})
    path = PRIVATE / "cloud-gateway.caddy"
    temporary = PRIVATE / f"cloud-gateway-{connection}.tmp"
    with os.fdopen(os.open(temporary, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600), "w") as file:
        file.write(
            f'@outsideGateway not header X-SkinCare-Gateway {keys["GATEWAY_TOKEN"]}\n'
            "respond @outsideGateway 404\n"
            "@gatewayProbe path /__gateway/probe\n"
            "header @gatewayProbe Content-Type application/json\n"
            f"respond @gatewayProbe \x60{payload}\x60 200\n"
        )
    temporary.replace(path)
    return keys, connection


def start(command):
    keys, connection = gateway_config()
    (ROOT / "distribution").mkdir(exist_ok=True)

    def compose(*args, **kwargs):
        return subprocess.run(command + list(args), cwd=ROOT, check=True, **kwargs)

    compose("run", "--rm", "--no-deps", "--entrypoint", "caddy", "edge",
            "validate", "--config", "/etc/caddy/Caddyfile", "--adapter", "caddyfile",
            capture_output=True)
    compose("build", "backend", "maintenance", "ai")
    compose("up", "-d", "--wait", "--wait-timeout", "900", "--force-recreate", "edge")
    compose("up", "-d", "--no-deps", "--force-recreate", "connector")
    headers = {"X-SkinCare-Gateway": keys["GATEWAY_TOKEN"]}

    def ready_tunnel():
        logs = compose("logs", "--no-color", "--tail", "100", "connector",
                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        urls = TUNNEL_URL.findall(logs.stdout)
        if not urls:
            return None
        url = urls[-1]
        probe = request(url, "/__gateway/probe", headers)
        if probe.get("connection") != connection:
            return None
        request(url, "/api/wc/auth/social/providers", headers)
        return url

    tunnel = wait_for(ready_tunnel, 150, "The protected connector did not become ready")
    request(ORIGIN, "/__skincare/connect", {"Authorization": "Bearer " + keys["CONNECT_TOKEN"]},
            {"origin": tunnel, "connection": connection})

    def public_ready():
        health = request(ORIGIN, "/__skincare/health")
        if health.get("connection") != connection:
            return False
        request(ORIGIN, "/api/wc/auth/social/providers")
        return True

    wait_for(public_ready, 240, "The fixed address has not reached this server")
    print("complete-cloud-fixed-route-ready", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", default=".env")
    parser.add_argument("--llm", action="store_true", help="Run the local questionnaire model with an NVIDIA GPU")
    parser.add_argument("--cpu", action="store_true", help="Run the local questionnaire model on CPU only (no GPU)")
    parser.add_argument("--check", action="store_true", help="Validate Compose without starting Docker services")
    parser.add_argument("--ensure", action="store_true",
                        help="Do nothing if the fixed address is healthy; otherwise (re)start and reconnect")
    args = parser.parse_args()
    command = compose_command(args.env_file, args.llm, args.cpu)
    try:
        verify_compose(command)
        if args.check:
            print("complete-cloud-compose-check")
            return
        if args.ensure and public_route_healthy():
            print("complete-cloud-route-already-healthy", flush=True)
            return
        start(command)
    except subprocess.CalledProcessError as error:
        parser.exit(1, f"Cloud Compose command failed at {error.cmd[len(command)]} (exit {error.returncode})\n")
    except (OSError, ValueError, RuntimeError) as error:
        parser.exit(1, f"Cloud deployment failed: {error}\n")


if __name__ == "__main__":
    main()

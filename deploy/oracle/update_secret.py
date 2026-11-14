"""Replace external-service keys in private/application-secrets.properties on the server.

Reads a JSON object from stdin (so values never appear in the process list or
shell history), e.g. {"GOOGLE_CLIENT_ID": "...", "GOOGLE_SECRET": "..."}.
Only the keys below may be changed. Prints key names, never values.
Run as root in the bundle folder:  sudo python3 update_secret.py < values.json
"""
import json
import os
from pathlib import Path
import sys

ALLOWED = {"GOOGLE_CLIENT_ID", "GOOGLE_SECRET", "KAKAO_CLIENT_ID", "KAKAO_CLIENT_SECRET",
           "NAVER_CLIENT_ID", "NAVER_CLIENT_SECRET",
           # data.go.kr key for HIRA hospital info + NMC hospital/pharmacy APIs
           "PUBLIC_MEDICAL_SERVICE_KEY"}
PATH = Path(__file__).resolve().parent / "private" / "application-secrets.properties"


def escape(value: str) -> str:
    # Same escaping as deploy/public-demo/prepare_bundle.py:property_value().
    return (value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r")
            .replace("\t", "\\t").replace("=", "\\=").replace(":", "\\:"))


def main() -> None:
    values = json.load(sys.stdin)
    if not isinstance(values, dict) or not values or set(values) - ALLOWED:
        raise SystemExit(f"only these keys may be set: {sorted(ALLOWED)}")
    for key, value in values.items():
        if not isinstance(value, str) or not value.strip() or len(value) > 512:
            raise SystemExit(f"{key}: empty or invalid value")
    stat = PATH.stat()
    lines = PATH.read_text(encoding="utf-8").splitlines()
    seen = set()
    for i, line in enumerate(lines):
        key = line.split("=", 1)[0]
        if key in values:
            lines[i] = f"{key}={escape(values[key].strip())}"
            seen.add(key)
    lines += [f"{k}={escape(v.strip())}" for k, v in values.items() if k not in seen]
    temp = PATH.with_name(PATH.name + ".new")
    temp.write_text("\n".join(lines) + "\n", encoding="utf-8")
    os.chown(temp, stat.st_uid, stat.st_gid)
    os.chmod(temp, stat.st_mode & 0o777)
    temp.replace(PATH)
    print("updated:", ", ".join(sorted(values)))


if __name__ == "__main__":
    main()

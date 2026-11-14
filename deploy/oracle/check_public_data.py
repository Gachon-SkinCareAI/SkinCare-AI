"""Check a data.go.kr service key against the three public APIs the backend uses.

  HIRA 병원정보서비스 (15001698)        admin "공공데이터 동기화" (hospitals)
  NMC 병·의원 목록 (15000736)          app hospital search + sync
  NMC 약국 목록 (15000576)             app pharmacy search + sync

Key source: JSON {"PUBLIC_MEDICAL_SERVICE_KEY": "..."} on stdin, or with
--current the value already in private/application-secrets.properties.
Prints results only, never the key. Exit 0 only when all three answer normally.
"""
import json
from pathlib import Path
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

SECRETS = Path(__file__).resolve().parent / "private" / "application-secrets.properties"
APIS = {
    "HIRA 병원정보서비스 (관리자 동기화)": "https://apis.data.go.kr/B551182/hospInfoServicev2/getHospBasisList",
    "NMC 병·의원 목록 (앱 병원 찾기)": "https://apis.data.go.kr/B552657/HsptlAsembySearchService/getHsptlMdcncListInfoInqire",
    "NMC 약국 목록 (앱 약국 찾기)": "https://apis.data.go.kr/B552657/ErmctInsttInfoInqireService/getParmacyListInfoInqire",
}


def unescape(value: str) -> str:
    out, i = [], 0
    while i < len(value):
        if value[i] == "\\" and i + 1 < len(value):
            i += 1
        out.append(value[i])
        i += 1
    return "".join(out)


def current_key() -> str:
    for line in SECRETS.read_text(encoding="utf-8").splitlines():
        if line.startswith("PUBLIC_MEDICAL_SERVICE_KEY="):
            return unescape(line.split("=", 1)[1].strip())
    return ""


def tag(body: str, name: str):
    m = re.search("<" + name + ">([^<]*)", body)
    return m.group(1) if m else None


def check(key: str) -> bool:
    ok_all = True
    for name, url in APIS.items():
        query = urllib.parse.urlencode({"serviceKey": urllib.parse.unquote(key), "pageNo": 1, "numOfRows": 1})
        try:
            with urllib.request.urlopen(url + "?" + query, timeout=20) as response:
                code, body = response.status, response.read(3000).decode("utf-8", "ignore")
        except urllib.error.HTTPError as error:
            code, body = error.code, error.read(3000).decode("utf-8", "ignore")
        except Exception as error:  # network trouble
            code, body = "ERR", str(error)
        result = tag(body, "resultCode") or tag(body, "returnReasonCode")
        ok = code == 200 and result == "00"
        ok_all &= ok
        message = tag(body, "resultMsg") or tag(body, "returnAuthMsg") or body[:80].replace("\n", " ").strip()
        print(f"  {'OK ' if ok else 'FAIL'} {name}: HTTP {code}, {message}, total={tag(body, 'totalCount')}")
    return ok_all


def main() -> None:
    if "--current" in sys.argv:
        key = current_key()
    else:
        key = str(json.load(sys.stdin).get("PUBLIC_MEDICAL_SERVICE_KEY", "")).strip()
    if not key:
        raise SystemExit("no key")
    print(f"key length {len(key)} ({'URL-encoded' if '%' in key else 'decoded'} form)")
    sys.exit(0 if check(key) else 1)


if __name__ == "__main__":
    main()

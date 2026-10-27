"""앱(Barcode.java)이 그리는 멤버십 바코드가 표준대로 읽히는지 확인한다.

- Code 128: python-barcode의 기호표로 막대를 다시 읽어 숫자·체크섬 확인
- EAN-13 / UPC-A: python-barcode가 만든 막대와 전부 비교

준비: pip install python-barcode, JDK(javac/java, JAVA_HOME 우선)
사용: python android/tools/check_barcode.py   (mvp 폴더에서)
"""
import os
import shutil
import subprocess
import sys
import tempfile

from barcode import EAN13, UPCA
from barcode.charsets import code128 as C

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "app", "src", "main", "java", "kr", "recatch", "proto", "Barcode.java")
CASES = [
    "7700192388321049:member16", "8806441022750193:member16", "261104205578:student12",
    "123456789012:numeric", "1234567:numeric", "98765432109:numeric",
    "8801234567893:ean13", "4006381333931:ean13", "036000291452:upca",
]


def tool(name):
    home = os.environ.get("JAVA_HOME")
    return os.path.join(home, "bin", name) if home else name


def main():
    out = tempfile.mkdtemp(prefix="recatch-barcode-")
    try:
        subprocess.check_call([tool("javac"), "-encoding", "UTF-8", "-d", out, SRC, os.path.join(HERE, "BarcodeCli.java")])
        rev = {v: i for i, v in enumerate(C.CODES)}
        stop = C.STOP + "11"  # 종료 막대 2모듈 포함
        fail = 0
        for case in CASES:
            digits, fmt = case.split(":")
            m = subprocess.check_output([tool("java"), "-cp", out, "BarcodeCli", digits, fmt], text=True).strip()
            if fmt in ("ean13", "upca"):
                ok = m == (EAN13 if fmt == "ean13" else UPCA)(digits).build()[0] or (fmt == "upca" and m == EAN13("0" + digits).build()[0])
            else:
                body = m[: -len(stop)] if m.endswith(stop) else ""
                vals = [rev.get(body[i:i + 11]) for i in range(0, len(body), 11)]
                ok = bool(vals) and None not in vals
                if ok:
                    *data, check = vals
                    text, cset = "", {103: "A", 104: "B", 105: "C"}[data[0]]
                    for v in data[1:]:
                        if v == 99:
                            cset = "C"
                        elif v == 100:
                            cset = "B"
                        else:
                            text += f"{v:02d}" if cset == "C" else chr(v + 32)
                    total = data[0] + sum(v * k for k, v in enumerate(data[1:], 1))
                    ok = total % 103 == check and text == digits
            print(("OK  " if ok else "DIFF"), case)
            fail += not ok
        print("모두 정상" if not fail else f"{fail}건 실패")
        return 1 if fail else 0
    finally:
        shutil.rmtree(out, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())

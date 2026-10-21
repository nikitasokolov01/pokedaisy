"""Reproduce HGSS rule metadata from a local public-source checkout; never fetch or build a ROM.

Example: python scripts/verify_hgss_rule_sources.py --source-dir <pokeheartgold>
The pinned Git blobs prevent a changed source tree from silently approving new constants.
"""

import argparse
import csv
import hashlib
import io
import json
from pathlib import Path
import re


COMMIT = "6988158691eb6178cff697cda0428045ddcba5fb"
BLOBS = {
    "src/item.c": "7d25cec3473623947aa5036c1588a3c3f5a8e12b",
    "src/field_move.c": "3962ccb2e87ca8477bd0fb700d3edff4134f7693",
    "include/constants/items.h": "1464215e7cc735ca1ec630ef76793c3c9fb12128",
    "include/constants/badge.h": "89e8fb6c4fc1a0106cfb46bef979355683e7e94c",
    "files/poketool/personal/growtbl.csv": "d8f48b04926336b6b0fca0136f3274134a647bfa",
}


def verify(root: Path) -> dict:
    sources = {}
    for name, expected in BLOBS.items():
        content = root.joinpath(name).read_bytes()
        # A Windows checkout may use CRLF; Git's canonical source blobs use LF.
        content = content.replace(b"\r\n", b"\n")
        actual = hashlib.sha1(b"blob " + str(len(content)).encode() + b"\0" + content).hexdigest()
        if actual != expected:
            raise ValueError(f"Source mismatch: {name}; expected pinned blob {expected}, got {actual}")
        sources[name] = content.decode("utf-8")

    items = dict(re.findall(r"#define ITEM_HM(\d+)\s+(\d+)\b", sources["include/constants/items.h"]))
    badges = dict(re.findall(r"#define (BADGE_\w+)\s+(\d+)\b", sources["include/constants/badge.h"]))
    moves = re.findall(r"MOVE_(\w+),\s*// HM(\d+)\b", sources["src/item.c"])
    if len(moves) != 8 or {number for _, number in moves} != {f"{n:02}" for n in range(1, 9)}:
        raise ValueError("Expected eight distinct native HM mappings")
    hm = []
    for move, number in moves:
        function = "FieldMove_Check" + move.title().replace("_", "")
        body = re.search(r"static u32 " + function + r"\([^;]*?\) \{(.*?)^\}",
                         sources["src/field_move.c"], re.S | re.M)
        if body is None:
            raise ValueError(f"Missing native check: {function}")
        badge = re.search(r"PlayerProfile_TestBadgeFlag\([^;\n]*?,\s*(BADGE_\w+)\)", body[1])
        if badge is None:
            raise ValueError(f"Missing badge guard: {function}")
        hm.append((move, int(items[number]), int(badges[badge[1]])))
    canonical = "".join(f"{move}:{item}:{badge}\n" for move, item, badge in hm)

    rows = list(csv.reader(io.StringIO(sources["files/poketool/personal/growtbl.csv"])))
    growth = {}
    for row in rows[1:7]:
        if len(row) != 102:
            raise ValueError("Expected levels 0 through 100")
        growth[row[0]] = hashlib.sha256(",".join(row[2:]).encode()).hexdigest()
    if len(growth) != 6:
        raise ValueError("Expected six used growth curves")
    return {"commit": COMMIT, "blobs_verified": BLOBS, "hm_mapping": hm,
            "hm_mapping_sha256": hashlib.sha256(canonical.encode()).hexdigest(),
            "growth_level_1_to_100_sha256": growth}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(verify(args.source_dir), indent=2))

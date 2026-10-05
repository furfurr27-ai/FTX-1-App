#!/usr/bin/env python3
"""Create an immutable FieldOps checkpoint snapshot and resume record."""
from __future__ import annotations
import argparse, datetime as dt, hashlib, json, os, pathlib, re, tarfile, tempfile

EXCLUDE_PREFIXES = (
    "checkpoints/snapshots/",
    "checkpoints/manifests/",
    ".git/",
)
EXCLUDE_EXACT = {
    "checkpoints/LATEST.json",
    "checkpoints/RESUME_HERE.md",
    "checkpoints/CURRENT_STATE.json",
}

def sha256_file(path: pathlib.Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()

def source_files(root: pathlib.Path):
    out=[]
    for p in root.rglob("*"):
        if not p.is_file():
            continue
        rel=p.relative_to(root).as_posix()
        if rel in EXCLUDE_EXACT or any(rel.startswith(x) for x in EXCLUDE_PREFIXES):
            continue
        out.append((rel,p))
    return sorted(out)

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--root", default=".")
    ap.add_argument("--id", required=True, help="e.g. CP-0001-GITHUB-SURVEY")
    ap.add_argument("--summary", required=True)
    ap.add_argument("--phase", required=True)
    ap.add_argument("--parent")
    ap.add_argument("--test-status", default="NOT_RUN")
    ap.add_argument("--completed", action="append", default=[])
    ap.add_argument("--blocker", action="append", default=[])
    ap.add_argument("--next", dest="next_actions", action="append", default=[])
    ap.add_argument("--source-pin", dest="source_pins", action="append", default=[], help="Exact upstream source pin, e.g. owner/repo@commit")
    args=ap.parse_args()

    if not re.fullmatch(r"CP-\d{4}-[A-Z0-9][A-Z0-9_-]*", args.id):
        raise SystemExit("Checkpoint id must look like CP-0001-NAME")

    root=pathlib.Path(args.root).resolve()
    cp=root/"checkpoints"
    manifests=cp/"manifests"; snapshots=cp/"snapshots"
    manifests.mkdir(parents=True, exist_ok=True); snapshots.mkdir(parents=True, exist_ok=True)
    mp=manifests/f"{args.id}.json"; sp=snapshots/f"{args.id}.tar.gz"
    if mp.exists() or sp.exists():
        raise SystemExit(f"Refusing to overwrite immutable checkpoint {args.id}")

    latest=cp/"LATEST.json"
    parent=args.parent
    if not parent and latest.exists():
        try: parent=json.loads(latest.read_text())["checkpoint_id"]
        except Exception: parent=None

    version=(root/"VERSION").read_text().strip() if (root/"VERSION").exists() else "unknown"
    files={rel:sha256_file(p) for rel,p in source_files(root)}
    created=dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat()
    manifest={
        "schema":1,
        "checkpoint_id":args.id,
        "parent":parent,
        "created_utc":created,
        "project_version":version,
        "summary":args.summary,
        "phase":args.phase,
        "test_status":args.test_status,
        "completed":args.completed,
        "blockers":args.blocker,
        "next_actions":args.next_actions,
        "source_pins":args.source_pins,
        "files":files,
        "snapshot_file":sp.relative_to(root).as_posix(),
        "snapshot_sha256":None,
    }
    mp.write_text(json.dumps(manifest,indent=2,sort_keys=True)+"\n")

    # Snapshot exactly the hashed working files plus this manifest.
    with tarfile.open(sp,"w:gz",format=tarfile.PAX_FORMAT) as tf:
        for rel,p in source_files(root):
            tf.add(p,arcname=rel,recursive=False)
        tf.add(mp,arcname=f"CHECKPOINT_MANIFEST/{mp.name}",recursive=False)

    manifest["snapshot_sha256"]=sha256_file(sp)
    mp.write_text(json.dumps(manifest,indent=2,sort_keys=True)+"\n")

    latest_data={
        "checkpoint_id":args.id,
        "created_utc":created,
        "manifest":mp.relative_to(root).as_posix(),
        "snapshot":sp.relative_to(root).as_posix(),
        "snapshot_sha256":manifest["snapshot_sha256"],
    }
    latest.write_text(json.dumps(latest_data,indent=2)+"\n")
    current={
        "project_version":version,
        "latest_checkpoint":args.id,
        "phase":args.phase,
        "test_status":args.test_status,
        "completed":args.completed,
        "blockers":args.blocker,
        "next_actions":args.next_actions,
        "updated_utc":created,
    }
    (cp/"CURRENT_STATE.json").write_text(json.dumps(current,indent=2)+"\n")

    lines=[
        "# RESUME HERE — FTX-1 FieldOps",
        "",
        f"Latest verified checkpoint: **{args.id}**",
        f"Project version: `{version}`",
        f"Phase: **{args.phase}**",
        f"Test status: **{args.test_status}**",
        "",
        "## What is complete in this checkpoint",
    ]
    lines += [f"- {x}" for x in args.completed] or ["- No completed items supplied."]
    lines += ["", "## Known blockers / red items"]
    lines += [f"- {x}" for x in args.blocker] or ["- None recorded."]
    lines += ["", "## Continue with these exact actions"]
    lines += [f"{i+1}. {x}" for i,x in enumerate(args.next_actions)] or ["1. No next actions supplied."]
    lines += [
        "",
        "## Verification before continuing",
        "Run:",
        "",
        "```bash",
        "python3 scripts/verify_checkpoint.py --root . --latest",
        "```",
        "",
        "Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.",
    ]
    (cp/"RESUME_HERE.md").write_text("\n".join(lines)+"\n")
    print(json.dumps(latest_data,indent=2))

if __name__=="__main__":
    main()

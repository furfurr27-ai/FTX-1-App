#!/usr/bin/env python3
"""Verify immutable FieldOps checkpoint archive and file hashes."""
from __future__ import annotations
import argparse, hashlib, json, pathlib, tarfile

def sha_bytes(b: bytes)->str:
    return hashlib.sha256(b).hexdigest()

def sha_file(p:pathlib.Path)->str:
    h=hashlib.sha256()
    with p.open('rb') as f:
        for c in iter(lambda:f.read(1024*1024),b''): h.update(c)
    return h.hexdigest()

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--root',default='.')
    g=ap.add_mutually_exclusive_group(required=True)
    g.add_argument('--latest',action='store_true')
    g.add_argument('--id')
    args=ap.parse_args()
    root=pathlib.Path(args.root).resolve()
    if args.latest:
        latest=json.loads((root/'checkpoints/LATEST.json').read_text())
        cid=latest['checkpoint_id']
    else: cid=args.id
    mp=root/'checkpoints/manifests'/f'{cid}.json'
    m=json.loads(mp.read_text())
    sp=root/m['snapshot_file']
    failures=[]
    actual=sha_file(sp)
    if actual!=m['snapshot_sha256']:
        failures.append(f"snapshot SHA mismatch expected={m['snapshot_sha256']} actual={actual}")
    with tarfile.open(sp,'r:gz') as tf:
        members={x.name:x for x in tf.getmembers() if x.isfile()}
        for rel,expected in m['files'].items():
            member=members.get(rel)
            if not member:
                failures.append(f'missing from snapshot: {rel}')
                continue
            f=tf.extractfile(member)
            actual_hash=sha_bytes(f.read()) if f else ''
            if actual_hash!=expected:
                failures.append(f'hash mismatch: {rel}')
    if failures:
        print(f'FAIL {cid}: {len(failures)} problem(s)')
        for x in failures: print(' -',x)
        raise SystemExit(1)
    print(f'PASS {cid}: snapshot and {len(m["files"])} file hashes verified')

if __name__=='__main__': main()

"""
Downloads Pexels photos listed in a manifest, for the hackathon projects. Reusable for every project.

Manifest (JSON):
    {
      "outDir": "frontend/public/hero",
      "size": "large2x",                 // large2x = ~1880px (hero banners), large = ~940px (cards)
      "images": { "login-1": "team office meeting", "login-2": "finance receipts" }
    }

Usage:
    set PEXELS_API_KEY=your-key          (PowerShell: $env:PEXELS_API_KEY = "your-key")
    python tools/fetch_images.py expense-management/images.json

Paths in outDir are relative to the manifest file. Existing files are skipped, so it is safe to re-run.
Photos are free to use under the Pexels licence.
"""

import json
import os
import sys
import time

import requests

BASE_URL = "https://api.pexels.com/v1/search"


def download(session, key, query, path, size):
    resp = session.get(
        BASE_URL,
        params={"query": query, "per_page": 8, "orientation": "landscape"},
        headers={"Authorization": key},
        timeout=30,
    )
    if resp.status_code in (401, 429):
        sys.exit(f"Pexels refused the request ({resp.status_code}). Check PEXELS_API_KEY and the rate limit.")
    resp.raise_for_status()
    photos = resp.json().get("photos", [])
    if not photos:
        print(f"  no results for '{query}'")
        return False
    photo = next((p for p in photos if p["width"] > p["height"]), photos[0])
    img = session.get(photo["src"][size], timeout=60)
    img.raise_for_status()
    with open(path, "wb") as f:
        f.write(img.content)
    print(f"  saved {os.path.relpath(path)}  (photo by {photo.get('photographer', '?')})")
    return True


def main():
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # photographer names can contain any character
    key = os.environ.get("PEXELS_API_KEY")
    if not key:
        sys.exit("Set the PEXELS_API_KEY environment variable first.")
    if len(sys.argv) != 2:
        sys.exit("Usage: python tools/fetch_images.py <manifest.json>")

    manifest_path = os.path.abspath(sys.argv[1])
    with open(manifest_path, encoding="utf-8") as f:
        manifest = json.load(f)
    out_dir = os.path.join(os.path.dirname(manifest_path), manifest["outDir"])
    os.makedirs(out_dir, exist_ok=True)

    session = requests.Session()
    for name, query in manifest["images"].items():
        path = os.path.join(out_dir, f"{name}.jpg")
        if os.path.exists(path):
            print(f"  have {name}.jpg")
            continue
        print(f"Fetching '{query}'...")
        download(session, key, query, path, manifest.get("size", "large"))
        time.sleep(0.4)


if __name__ == "__main__":
    main()

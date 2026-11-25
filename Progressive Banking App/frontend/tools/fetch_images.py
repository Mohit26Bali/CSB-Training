"""
Downloads the photos used by the banking UI from Pexels into a staging folder, one subfolder per keyword.
Curate the results, then copy the keepers into public/images/ (see src/assets/images.ts for the names the app expects).

Usage (the key is read from the environment, never stored in the repo):
    set PEXELS_API_KEY=...        (PowerShell: $env:PEXELS_API_KEY="...")
    python tools/fetch_images.py [output_dir]

Free key: https://www.pexels.com/api/
"""

import os
import sys
import time

import requests

API_KEY = os.environ.get("PEXELS_API_KEY", "")
OUTPUT_DIR = sys.argv[1] if len(sys.argv) > 1 else "staging_images"

# (keyword, how many candidates, orientation)
KEYWORDS = [
    ("modern glass bank building", 4, "landscape"),
    ("financial district skyline dusk", 4, "landscape"),
    ("business team meeting modern office", 4, "landscape"),
    ("woman using smartphone mobile banking", 4, "landscape"),
    ("happy family new home", 4, "landscape"),
    ("couple planning finances laptop", 4, "landscape"),
    ("small business owner shop", 4, "landscape"),
    ("professional portrait smiling businesswoman", 3, "portrait"),
    ("professional portrait smiling businessman", 3, "portrait"),
]

BASE_URL = "https://api.pexels.com/v1/search"


def slug(text: str) -> str:
    return text.replace(" ", "_").lower()


def fetch(keyword: str, count: int, orientation: str, session: requests.Session) -> int:
    folder = os.path.join(OUTPUT_DIR, slug(keyword))
    os.makedirs(folder, exist_ok=True)
    resp = session.get(
        BASE_URL,
        params={"query": keyword, "per_page": count, "orientation": orientation},
        headers={"Authorization": API_KEY},
        timeout=30,
    )
    if resp.status_code in (401, 429):
        print(f"  Pexels refused the request ({resp.status_code}).")
        return 0
    resp.raise_for_status()
    saved = 0
    for photo in resp.json().get("photos", []):
        url = photo["src"]["large2x"] if orientation == "landscape" else photo["src"]["large"]
        path = os.path.join(folder, f"{slug(keyword)}_{photo['id']}.jpg")
        data = session.get(url, timeout=60)
        data.raise_for_status()
        with open(path, "wb") as handle:
            handle.write(data.content)
        saved += 1
        print(f"  saved {path}")
        time.sleep(0.3)
    return saved


def main() -> None:
    if not API_KEY:
        print("Set PEXELS_API_KEY first.")
        return
    session = requests.Session()
    total = 0
    for keyword, count, orientation in KEYWORDS:
        print(f"Fetching '{keyword}'...")
        total += fetch(keyword, count, orientation, session)
    print(f"Done: {total} images in {OUTPUT_DIR}/")


if __name__ == "__main__":
    main()

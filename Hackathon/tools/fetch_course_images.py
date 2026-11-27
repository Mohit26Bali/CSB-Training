"""
Downloads one Pexels photo per training course (plus the login-page hero images) into the frontend's
public folder. Derived from fetch_hero_images.py; the API key comes from the environment instead of the file.

Usage (from the enrollment folder):
    set PEXELS_API_KEY=your-key            (PowerShell: $env:PEXELS_API_KEY = "your-key")
    python tools/fetch_course_images.py

Re-running skips files that already exist. Photos are free to use under the Pexels licence.
"""

import os
import sys
import time

import requests

API_KEY = os.environ.get("PEXELS_API_KEY")
BASE_URL = "https://api.pexels.com/v1/search"
PUBLIC = os.path.join(os.path.dirname(__file__), "..", "enrollment", "frontend", "public")

# file name (slug of the course title) -> search query
COURSES = {
    "java-basics": "java programming code laptop",
    "spring-boot": "software developer backend code screen",
    "sql-fundamentals": "database server data center",
    "cyber-awareness": "cyber security padlock laptop",
    "docker": "shipping containers port",
    "docker-essentials": "shipping containers stacked",
    "python-for-data-analysis": "data analysis charts laptop",
    "react-fundamentals": "web developer coding monitor",
    "cloud-computing-with-aws": "cloud computing server technology",
    "kubernetes-in-practice": "network cables server rack",
    "agile-and-scrum-essentials": "sticky notes kanban board team",
    "effective-communication": "business meeting conversation",
    "leadership-for-new-managers": "team leader presentation office",
    "data-privacy-and-gdpr": "privacy data protection lock",
    "git-and-version-control": "programmer code on screen dark",
    "ux-design-basics": "ux wireframe sketch designer",
    "time-management": "planner calendar desk coffee",
    "machine-learning-intro": "artificial intelligence neural network",
    "old-mainframe": "vintage computer retro",
    "legacy-systems": "old computer keyboard",
}

HERO = {
    "login-1": "team learning workshop office",
    "login-2": "modern office collaboration",
    "login-3": "students studying together library",
}


def download(session, query, path, size):
    resp = session.get(
        BASE_URL,
        params={"query": query, "per_page": 8, "orientation": "landscape"},
        headers={"Authorization": API_KEY},
        timeout=30,
    )
    if resp.status_code in (401, 429):
        sys.exit(f"Pexels refused the request ({resp.status_code}). Check PEXELS_API_KEY / rate limit.")
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
    if not API_KEY:
        sys.exit("Set the PEXELS_API_KEY environment variable first.")
    session = requests.Session()
    jobs = [(COURSES, "course-images", "large"), (HERO, "hero", "large2x")]
    for mapping, folder, size in jobs:
        out = os.path.join(PUBLIC, folder)
        os.makedirs(out, exist_ok=True)
        for name, query in mapping.items():
            path = os.path.join(out, f"{name}.jpg")
            if os.path.exists(path):
                print(f"  have {name}.jpg")
                continue
            print(f"Fetching '{query}'...")
            download(session, query, path, size)
            time.sleep(0.4)


if __name__ == "__main__":
    main()

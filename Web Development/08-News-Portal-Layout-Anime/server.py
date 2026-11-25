"""Panel & Pixel - small local server: serves the website and the live anime news feeds.

Browsers are not allowed to read the Crunchyroll, Anime News Network and MyAnimeList news feeds
directly (the feeds do not send CORS headers), so this server fetches them and returns them as JSON
at /api/news. It uses only Python's standard library, so nothing needs to be installed.

Run:  python server.py        (opens http://localhost:8000 in your browser)
      python server.py --no-browser
Stop: Ctrl+C
"""

import hashlib
import html
import json
import re
import sys
import threading
import time
import urllib.request
import webbrowser
import xml.etree.ElementTree as ET
from email.utils import parsedate_to_datetime
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

PORT = 8000
FOLDER = Path(__file__).resolve().parent
CACHE_SECONDS = 300

FEEDS = [
    ("Crunchyroll News", "https://cr-news-api-service.prd.crunchyrollsvc.com/v1/en-US/rss"),
    ("Anime News Network", "https://www.animenewsnetwork.com/news/rss.xml"),
    ("MyAnimeList", "https://myanimelist.net/rss/news.xml"),
]

cache = {"time": 0, "data": None}
cache_lock = threading.Lock()


def plain_text(text):
    """Remove HTML tags and decode entities such as &#039;."""
    text = re.sub(r"<[^>]+>", " ", text or "")
    return re.sub(r"\s+", " ", html.unescape(text)).strip()


def child(item, name):
    """Find a child element by its local name, ignoring XML namespaces (media:thumbnail, dc:creator)."""
    for element in item:
        if element.tag.split("}")[-1] == name:
            return element
    return None


def read_feed(source, url):
    request = urllib.request.Request(url, headers={"User-Agent": "PanelAndPixel/1.0 (student demo)"})
    with urllib.request.urlopen(request, timeout=15) as response:
        root = ET.fromstring(response.read())

    stories = []
    for item in root.iter("item"):
        link = (item.findtext("link") or "").strip()
        title = plain_text(item.findtext("title"))
        if not link or not title:
            continue
        thumbnail = child(item, "thumbnail")
        image = ""
        if thumbnail is not None:
            image = thumbnail.get("url") or (thumbnail.text or "").strip()
        author = child(item, "creator") if child(item, "creator") is not None else child(item, "author")
        published = parsedate_to_datetime(item.findtext("pubDate")).astimezone().isoformat()
        stories.append({
            "id": hashlib.sha1(link.encode()).hexdigest()[:12],
            "title": title,
            "summary": plain_text(item.findtext("description"))[:400],
            "url": link,
            "image": image,
            "source": source,
            "author": plain_text(author.text) if author is not None and author.text else "",
            "category": plain_text(item.findtext("category")),
            "published": published,
        })
    return stories


def latest_news():
    """All feeds merged, newest first. Cached for 5 minutes; a failed feed is skipped."""
    with cache_lock:
        if cache["data"] and time.time() - cache["time"] < CACHE_SECONDS:
            return cache["data"]
        stories, sources = [], []
        for source, url in FEEDS:
            try:
                stories.extend(read_feed(source, url))
                sources.append(source)
            except Exception as error:
                print(f"  {source} feed failed: {error}")
        if not stories:
            raise RuntimeError("no feed could be read")
        stories.sort(key=lambda story: story["published"], reverse=True)
        cache["data"] = {"updated": time.strftime("%Y-%m-%dT%H:%M:%S%z"), "sources": sources, "stories": stories[:60]}
        cache["time"] = time.time()
        return cache["data"]


class Handler(SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path.split("?")[0] == "/api/news":
            try:
                body = json.dumps(latest_news(), ensure_ascii=False).encode("utf-8")
                self.send_response(200)
            except Exception as error:
                body = json.dumps({"error": str(error)}).encode("utf-8")
                self.send_response(502)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        super().do_GET()

    def log_message(self, format, *args):
        if "/api/news" in (args[0] if args else ""):
            print(f"  news requested ({args[1]})")


if __name__ == "__main__":
    server = ThreadingHTTPServer(("127.0.0.1", PORT), partial(Handler, directory=str(FOLDER)))
    url = f"http://localhost:{PORT}/"
    print(f"Panel & Pixel is running at {url}")
    print("Press Ctrl+C to stop.")
    if "--no-browser" not in sys.argv:
        threading.Timer(1.0, lambda: webbrowser.open(url)).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopped.")

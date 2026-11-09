from flask import Flask, request, Response
import requests
import re
import os

app = Flask(__name__)

DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

@app.route("/")
def index():
    return "Ciro CCTV Proxy Relay is Running 🚀", 200

@app.route("/health")
def health():
    return {"status": "ok", "service": "ciro-cctv-proxy"}, 200

@app.route("/proxy")
def proxy():
    target_url = request.args.get("url")
    if not target_url:
        return "Missing 'url' query parameter", 400

    headers = {
        "User-Agent": request.headers.get("User-Agent") or DEFAULT_UA,
        "Accept": "*/*"
    }

    if "Range" in request.headers:
        headers["Range"] = request.headers["Range"]

    # Spoof referer based on Korean target domain
    if "its.go.kr" in target_url or "ktict.co.kr" in target_url:
        headers["Referer"] = "https://its.go.kr/"
        headers["Origin"] = "https://its.go.kr"
    elif "utic.go.kr" in target_url:
        headers["Referer"] = "https://www.utic.go.kr/"
        headers["Origin"] = "https://www.utic.go.kr"
    elif "hrfco.go.kr" in target_url:
        headers["Referer"] = "https://hrfco.go.kr/sumun/cctvPopup.do"
        headers["Origin"] = "https://hrfco.go.kr"
    elif "gitsview.gg.go.kr" in target_url or "gg.go.kr" in target_url:
        headers["Referer"] = "https://gitsview.gg.go.kr/"
        headers["Origin"] = "https://gitsview.gg.go.kr"
    elif "topis.seoul.go.kr" in target_url:
        headers["Referer"] = "https://topis.seoul.go.kr/"

    try:
        req = requests.get(target_url, headers=headers, stream=True, timeout=10, allow_redirects=True)
        content_type = req.headers.get("Content-Type", "")

        is_m3u8 = ".m3u8" in target_url or "mpegurl" in content_type.lower()
        if is_m3u8 and req.status_code == 200:
            text = req.text
            base_url = target_url.rsplit("/", 1)[0]

            def replace_line(match):
                line = match.group(0).strip()
                if line.startswith("#") or not line:
                    return line
                if line.startswith("http://") or line.startswith("https://"):
                    full = line
                elif line.startswith("/"):
                    from urllib.parse import urlparse
                    p = urlparse(target_url)
                    full = f"{p.scheme}://{p.netloc}{line}"
                else:
                    full = f"{base_url}/{line}"
                return f"/proxy?url={full}"

            rewritten_text = re.sub(r'^(?!#).+$', replace_line, text, flags=re.MULTILINE)
            response = Response(rewritten_text, status=req.status_code, mimetype="application/vnd.apple.mpegurl")
        else:
            response = Response(req.iter_content(chunk_size=65536), status=req.status_code, content_type=content_type)

        response.headers["Access-Control-Allow-Origin"] = "*"
        response.headers["Access-Control-Allow-Methods"] = "GET, HEAD, OPTIONS"
        response.headers["Access-Control-Allow-Headers"] = "*"
        response.headers["Access-Control-Expose-Headers"] = "Content-Length, Content-Type, Range"
        return response

    except Exception as e:
        return f"Proxy error: {str(e)}", 502

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8080))
    app.run(host="0.0.0.0", port=port)

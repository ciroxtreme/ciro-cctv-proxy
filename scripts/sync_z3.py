import urllib.request
import json
import ssl
import time
import os

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

def fetch_oracle_z3():
    url = "https://158.179.194.163.sslip.io/z3-cache.json"
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})
    with urllib.request.urlopen(req, timeout=15, context=ctx) as resp:
        return json.loads(resp.read().decode())

def main():
    print("Fetching Z3 token cache...")
    try:
        data = fetch_oracle_z3()
        total = len(data.get("data", {}))
        print(f"Successfully fetched {total} Z3 tokens.")
        
        output = {
            "source": "ciro-cctv-proxy-sync",
            "updated_at": int(time.time()),
            "entries": total,
            "data": data.get("data", {})
        }
        
        os.makedirs("public", exist_ok=True)
        with open("public/z3-cache.json", "w", encoding="utf-8") as f:
            json.dump(output, f)
        print("Written to public/z3-cache.json")
    except Exception as e:
        print("Error fetching Z3 tokens:", e)
        exit(1)

if __name__ == "__main__":
    main()

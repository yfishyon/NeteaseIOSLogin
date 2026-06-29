#!/usr/bin/env python3
import urllib.request, ssl, re, os, sys

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

url = sys.argv[1]
req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
data = urllib.request.urlopen(req, timeout=30, context=ctx).read().decode("utf-8", "ignore")

m = re.search(r'android_link\s*=\s*android_type\s*\?\s*"([^"]+)"', data)
if not m:
    m = re.search(r'android_link\s*=\s*[^"]*"([^"]+\.apk[^"]*)"', data)
if not m:
    raise SystemExit("android_link not found: " + url)

link = m.group(1)
name = link.split("?")[0].split("/")[-1]

gh_out = os.environ.get("GITHUB_OUTPUT", "/dev/null")
with open(gh_out, "a") as f:
    f.write(f"name={name}\n")
    f.write(f"url={link}\n")
print("name:", name)
print("url:", link)

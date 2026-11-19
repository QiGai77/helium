# Render SVG -> 2x PNG with headless Chromium (Playwright).
import sys, re, os
from playwright.sync_api import sync_playwright
def render(pairs, scale=2):
    with sync_playwright() as p:
        b = p.chromium.launch(args=["--no-sandbox"])
        for svg, png in pairs:
            s = open(svg, encoding='utf-8').read()
            m = re.search(r'<svg[^>]*\swidth="(\d+)"[^>]*\sheight="(\d+)"', s)
            w, h = int(m.group(1)), int(m.group(2))
            pg = b.new_page(viewport={'width': w, 'height': h}, device_scale_factor=scale)
            pg.set_content(f'<html><body style="margin:0">{s}</body></html>')
            pg.evaluate("document.fonts.ready")
            pg.wait_for_timeout(400)
            pg.screenshot(path=png, clip={'x': 0, 'y': 0, 'width': w, 'height': h})
            pg.close()
        b.close()
if __name__ == '__main__':
    a = sys.argv[1:]; render(list(zip(a[::2], a[1::2])))

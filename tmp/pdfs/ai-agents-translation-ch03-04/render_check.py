from pathlib import Path
import json
from playwright.sync_api import sync_playwright

root = Path(__file__).resolve().parents[3]
out = root / 'output/playwright/ai-agents-translation-ch03-04'
out.mkdir(parents=True, exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(
        executable_path='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
        headless=True,
    )
    page = browser.new_page(viewport={'width': 1280, 'height': 1000}, device_scale_factor=1)
    page.route('http://**/*', lambda route: route.abort())
    page.route('https://**/*', lambda route: route.abort())
    page.goto(Path(__file__).with_name('preview.html').as_uri())
    page.screenshot(path=str(out / 'opening.png'))
    result = {
        'images': page.locator('img').count(),
        'tables': page.locator('table').count(),
        'code_blocks': page.locator('pre').count(),
        'all_images_loaded': page.locator('img').evaluate_all(
            '(xs)=>xs.every(x=>x.complete&&x.naturalWidth>0)'
        ),
        'horizontal_document_overflow': page.evaluate(
            'document.documentElement.scrollWidth > window.innerWidth'
        ),
    }
    for i in range(page.locator('table').count()):
        page.locator('table').nth(i).screenshot(path=str(out / f'table-{i+1}.png'))
    for i in [1, 9, 12, 26, 29]:
        page.locator('pre').nth(i).screenshot(path=str(out / f'code-{i}.png'))
    page.locator('img').nth(15).screenshot(path=str(out / 'figure-team.png'))
    print(json.dumps(result))
    Path(__file__).with_name('render-results.json').write_text(json.dumps(result, indent=2))
    browser.close()

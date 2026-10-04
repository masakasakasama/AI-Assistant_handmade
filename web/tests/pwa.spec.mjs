import {test,expect} from '@playwright/test';
test('installed assets are cached, old Tatsu caches are removed, and Chromium reloads offline',async({browser,browserName})=>{
  const context=await browser.newContext({serviceWorkers:'allow',viewport:{width:390,height:844}});
  const page=await context.newPage();
  await page.addInitScript(()=>{void caches.open('tatsu-old-fixture');void caches.open('unrelated-fixture');});
  await page.goto(process.env.WEB_BASE_URL||'http://127.0.0.1:4173');
  await page.evaluate(()=>navigator.serviceWorker.ready);
  await expect.poll(()=>page.evaluate(async()=>{const keys=await caches.keys();return keys.includes('unrelated-fixture')&&!keys.includes('tatsu-old-fixture');})).toBe(true);
  // A newly claiming worker can trigger the app's controllerchange reload.
  // Wait across that navigation instead of evaluating in a disappearing document.
  await page.waitForFunction(()=>!!navigator.serviceWorker.controller);
  await page.reload();
  await expect.poll(()=>page.evaluate(()=>window.tatsuMascotDiagnostics?.()?.frames||0),{timeout:25_000}).toBeGreaterThan(3);
  await context.setOffline(true);
  expect(await page.evaluate(async()=>{
    const home=await caches.match('/'),model=await caches.match('/mascot.glb');
    if(!home||!model)return false;const html=await home.text();const asset=/src="([^"]+\.js)"/.exec(html)?.[1];
    return !!asset&&!!(await caches.match(asset))&&(await model.arrayBuffer()).byteLength>300000;
  })).toBe(true);
  // Playwright's WebKit offline override fails navigation before SW interception.
  // Validate complete cache content there; only Chromium's offline reload is proven.
  if(browserName==='chromium'){
    await page.reload();await expect(page.locator('h1')).toHaveText('おかえりなさい');
    await expect.poll(()=>page.evaluate(()=>window.tatsuMascotDiagnostics?.()?.frames||0),{timeout:25_000}).toBeGreaterThan(3);
  }
  await context.close();
});

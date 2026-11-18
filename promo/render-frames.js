const puppeteer = require('puppeteer-core');
const path = require('path');
const fs = require('fs');

async function main() {
  const chromePath = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
  const htmlPath = 'file:///' + path.resolve(__dirname, 'index.html').replace(/\\/g, '/');
  const framesDir = path.resolve(__dirname, 'frames');

  if (!fs.existsSync(framesDir)) {
    fs.mkdirSync(framesDir, { recursive: true });
  }

  console.log('Launching Chrome from:', chromePath);
  const browser = await puppeteer.launch({
    executablePath: chromePath,
    headless: 'new',
    args: [
      '--no-sandbox',
      '--disable-setuid-sandbox',
      '--disable-dev-shm-usage',
      '--window-size=1920,1080'
    ]
  });

  const page = await browser.newPage();
  await page.setViewport({ width: 1920, height: 1080, deviceScaleFactor: 1 });

  console.log('Loading page:', htmlPath);
  await page.goto(htmlPath, { waitUntil: 'networkidle0' });

  // Wait for fonts and window.OPENER
  await page.waitForFunction(() => window.OPENER && window.OPENER.ready);

  console.log('OPENER ready! Pausing timeline...');
  await page.evaluate(() => {
    window.OPENER.tl.pause();
  });

  // Let's sample frames
  // The full sequence is 0 to 20 seconds.
  // For the README hero GIF, let's capture the key action from:
  // 4.0s (Phone revelation) through 18.5s (Brand Logo Sting & CTA)
  // Or let's see: 4.0s to 18.0s is 14 seconds. At 18 fps = ~250 frames.
  const fps = 18;
  const startTime = 3.5;
  const endTime = 18.5;
  const totalFrames = Math.round((endTime - startTime) * fps);

  console.log(`Rendering ${totalFrames} frames from ${startTime}s to ${endTime}s at ${fps} fps...`);

  for (let i = 0; i <= totalFrames; i++) {
    const time = startTime + (i / fps);
    await page.evaluate((t) => {
      window.OPENER.seek(t);
    }, time);

    // Give canvas waveform and requestAnimationFrame a tiny tick
    await page.evaluate(() => new Promise(r => requestAnimationFrame(r)));

    const filename = path.join(framesDir, `frame_${String(i).padStart(4, '0')}.png`);
    await page.screenshot({ path: filename, type: 'png' });

    if (i % 25 === 0 || i === totalFrames) {
      console.log(`Rendered frame ${i}/${totalFrames} (${Math.round(i / totalFrames * 100)}%) [t=${time.toFixed(2)}s]`);
    }
  }

  await browser.close();
  console.log('All frames captured successfully!');
}

main().catch(err => {
  console.error('Render error:', err);
  process.exit(1);
});

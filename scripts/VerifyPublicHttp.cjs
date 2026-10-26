// Read-only HTTP checks against the running application and its linked assets.
const base = process.env.APP_PUBLIC_URL || 'http://localhost:8080';
const checks = [
    ['/', 200], ['/help', 200], ['/help?q=password', 200],
    ['/help?categoryId=99999999', 200], ['/privacy', 200], ['/login', 200],
    ['/forgot-password', 200], ['/reset-password?token=invalid-verification-token', 200],
    ['/student/dashboard', 302], ['/admin/users', 302],
    ['/help?categoryId=invalid', 400], ['/images/nonexistent-verification-image.svg', 404]
];

(async () => {
    let failures = 0;
    const assets = new Set();
    for (const [path, expected] of checks) {
        const response = await fetch(new URL(path, base), { redirect: 'manual', signal: AbortSignal.timeout(15000) });
        const html = await response.text();
        const ok = response.status === expected;
        console.log(`${ok ? 'PASS' : 'FAIL'} ${path} expected=${expected} actual=${response.status}`);
        if (!ok) failures++;
        if (response.status === 200) {
            for (const match of html.matchAll(/(?:src|href)="([^"#]+)"/g)) {
                const url = new URL(match[1].replaceAll('&amp;', '&'), base);
                if (url.origin === new URL(base).origin && /\.(css|js|svg|png|jpg|ico)$/.test(url.pathname)) assets.add(url.pathname);
            }
        }
    }
    for (const path of assets) {
        const response = await fetch(new URL(path, base), { redirect: 'manual', signal: AbortSignal.timeout(15000) });
        console.log(`${response.status === 200 ? 'PASS' : 'FAIL'} asset ${path} ${response.status}`);
        if (response.status !== 200) failures++;
        await response.arrayBuffer();
    }
    console.log(`Checks=${checks.length + assets.size} failures=${failures}`);
    process.exitCode = failures ? 1 : 0;
})().catch(error => { console.error(error); process.exitCode = 1; });

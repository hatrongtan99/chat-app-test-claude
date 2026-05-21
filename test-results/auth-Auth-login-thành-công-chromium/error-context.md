# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: auth.spec.ts >> Auth >> login thành công
- Location: e2e\auth.spec.ts:21:7

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator:  locator('#app-section')
Expected: visible
Received: hidden
Timeout:  5000ms

Call log:
  - Expect "toBeVisible" with timeout 5000ms
  - waiting for locator('#app-section')
    14 × locator resolved to <div id="app-section">…</div>
       - unexpected value "hidden"

```

```yaml
- heading "Login" [level=2]
- text: Invalid username or password
- textbox "Username": testuser
- textbox "Password": password123
- button "Login"
- text: Don't have an account? Register
```

# Test source

```ts
  1  | import { test, expect } from '@playwright/test';
  2  | 
  3  | test.describe('Auth', () => {
  4  |   test.beforeEach(async ({ page }) => {
  5  |     await page.goto('/');
  6  |   });
  7  | 
  8  |   test('hiển thị form login mặc định', async ({ page }) => {
  9  |     await expect(page.locator('#auth-section')).toBeVisible();
  10 |     await expect(page.locator('#username')).toBeVisible();
  11 |     await expect(page.locator('#password')).toBeVisible();
  12 |     await expect(page.locator('#auth-btn')).toHaveText('Login');
  13 |   });
  14 | 
  15 |   test('chuyển sang form register', async ({ page }) => {
  16 |     await page.locator('#auth-toggle span').click();
  17 |     await expect(page.locator('#auth-btn')).toHaveText('Register');
  18 |     await expect(page.locator('#email')).toBeVisible();
  19 |   });
  20 | 
  21 |   test('login thành công', async ({ page }) => {
  22 |     await page.locator('#username').fill('testuser');
  23 |     await page.locator('#password').fill('password123');
  24 |     await page.locator('#auth-btn').click();
  25 | 
  26 |     // Sau login thành công, app section hiện ra
> 27 |     await expect(page.locator('#app-section')).toBeVisible({ timeout: 5000 });
     |                                                ^ Error: expect(locator).toBeVisible() failed
  28 |   });
  29 | 
  30 |   test('login sai credentials hiển thị lỗi', async ({ page }) => {
  31 |     await page.locator('#username').fill('wronguser');
  32 |     await page.locator('#password').fill('wrongpass');
  33 |     await page.locator('#auth-btn').click();
  34 | 
  35 |     await expect(page.locator('#error-msg')).toBeVisible({ timeout: 5000 });
  36 |     await expect(page.locator('#error-msg')).not.toBeEmpty();
  37 |   });
  38 | });
  39 | 
```
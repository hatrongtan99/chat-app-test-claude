import { test, expect } from '@playwright/test';

test.describe('Auth', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('hiển thị form login mặc định', async ({ page }) => {
    await expect(page.locator('#auth-section')).toBeVisible();
    await expect(page.locator('#username')).toBeVisible();
    await expect(page.locator('#password')).toBeVisible();
    await expect(page.locator('#auth-btn')).toHaveText('Login');
  });

  test('chuyển sang form register', async ({ page }) => {
    await page.locator('#auth-toggle span').click();
    await expect(page.locator('#auth-btn')).toHaveText('Register');
    await expect(page.locator('#email')).toBeVisible();
  });

  test('login thành công', async ({ page }) => {
    await page.locator('#username').fill('testuser');
    await page.locator('#password').fill('password123');
    await page.locator('#auth-btn').click();

    // Sau login thành công, app section hiện ra
    await expect(page.locator('#app-section')).toBeVisible({ timeout: 5000 });
  });

  test('login sai credentials hiển thị lỗi', async ({ page }) => {
    await page.locator('#username').fill('wronguser');
    await page.locator('#password').fill('wrongpass');
    await page.locator('#auth-btn').click();

    await expect(page.locator('#error-msg')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('#error-msg')).not.toBeEmpty();
  });
});

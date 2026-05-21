import { test, expect, Page } from '@playwright/test';

async function login(page: Page, username = 'testuser', password = 'password123') {
  await page.goto('/');
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('#auth-btn').click();
  await expect(page.locator('#app-section')).toBeVisible({ timeout: 5000 });
}

test.describe('Chat rooms', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('hiển thị tab Rooms mặc định', async ({ page }) => {
    await expect(page.locator('#tab-btn-rooms')).toHaveClass(/active/);
  });

  test('chuyển sang tab DMs', async ({ page }) => {
    await page.locator('#tab-btn-dms').click();
    await expect(page.locator('#tab-btn-dms')).toHaveClass(/active/);
  });

  test('tạo phòng mới', async ({ page }) => {
    page.on('dialog', dialog => dialog.accept('Test Room'));
    await page.locator('#new-room-btn').click();
    // Chờ room mới xuất hiện trong list
    await expect(page.locator('.room-item').filter({ hasText: 'Test Room' })).toBeVisible({ timeout: 5000 });
  });

  test('gửi tin nhắn trong phòng', async ({ page }) => {
    // Click vào room đầu tiên
    await page.locator('.room-item').first().click();
    await expect(page.locator('#message-input')).toBeVisible();

    await page.locator('#message-input').fill('Hello Playwright!');
    await page.locator('#send-btn').click();

    await expect(page.locator('#message-input')).toHaveValue('');
  });

  test('logout thành công', async ({ page }) => {
    await page.locator('#logout-btn').click();
    await expect(page.locator('#auth-section')).toBeVisible({ timeout: 3000 });
  });
});

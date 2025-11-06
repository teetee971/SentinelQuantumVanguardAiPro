import { test, expect } from '@playwright/test';

test.describe('Verification pages', () => {
  test('Particulier page loads', async ({ page }) => {
    await page.goto('/verification/particulier');
    await expect(page.getByRole('heading', { name: /Vérification — Compte Particulier/i })).toBeVisible();
  });

  test('Professionnel page loads', async ({ page }) => {
    await page.goto('/verification/professionnel');
    await expect(page.getByRole('heading', { name: /Vérification — Compte Professionnel/i })).toBeVisible();
  });
});

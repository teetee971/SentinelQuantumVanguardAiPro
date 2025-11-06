import { test, expect } from '@playwright/test';

test.describe('Pricing page', () => {
  test('loads and shows all plans', async ({ page }) => {
    await page.goto('/pricing');
    await expect(page.getByRole('heading', { name: 'Tarification' })).toBeVisible();

    // Individuals
    await expect(page.getByText('Freemium')).toBeVisible();
    await expect(page.getByText('Starter')).toBeVisible();
    await expect(page.getByText('Pro')).toBeVisible();

    // Business
    await expect(page.getByText('Developer (Freemium)')).toBeVisible();
    await expect(page.getByText('Pro (Équipe)')).toBeVisible();
    await expect(page.getByText('Business')).toBeVisible();
    await expect(page.getByText('Enterprise')).toBeVisible();
  });

  test('CTAs navigate to verification pages', async ({ page }) => {
    await page.goto('/pricing');

    // Click one of the individual CTAs
    const indivCTA = page.getByRole('link', { name: /Souscrire/i }).first();
    await indivCTA.click();
    await expect(page).toHaveURL(/\/verification\/particulier/);

    // Back and click a business CTA
    await page.goto('/pricing');
    const bizCTA = page.getByRole('link', { name: /Parler à l’équipe|Contacter\/Vérifier|Obtenir un devis/i }).first();
    await bizCTA.click();
    await expect(page).toHaveURL(/\/verification\/professionnel/);
  });
});

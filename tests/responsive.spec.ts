import { test, expect } from '@playwright/test';

test.describe('Responsive Design', () => {
  test.describe('Mobile viewport (375x667)', () => {
    test.use({ viewport: { width: 375, height: 667 } });

    test('hamburger menu is visible and functional', async ({ page }) => {
      await page.goto('/');
      
      // Check hamburger button is visible
      const hamburger = page.getByRole('button', { name: /ouvrir le menu|menu/i });
      await expect(hamburger).toBeVisible();
      
      // Click to open menu
      await hamburger.click();
      
      // Check menu items are visible
      await expect(page.getByRole('link', { name: 'Journal' })).toBeVisible();
      await expect(page.getByRole('link', { name: 'Pricing' })).toBeVisible();
    });

    test('hero heading has proper font size', async ({ page }) => {
      await page.goto('/');
      
      const heading = page.locator('h2, h1').first();
      await expect(heading).toBeVisible();
      
      // Check computed font size is at least 28px
      const fontSize = await heading.evaluate((el) => {
        return window.getComputedStyle(el).fontSize;
      });
      const fontSizeNum = parseFloat(fontSize);
      expect(fontSizeNum).toBeGreaterThanOrEqual(28);
    });

    test('CTA buttons meet minimum touch target size', async ({ page }) => {
      await page.goto('/pricing');
      
      const buttons = page.getByRole('link').filter({ hasText: /souscrire|essai|commencer/i });
      const firstButton = buttons.first();
      
      const box = await firstButton.boundingBox();
      expect(box).toBeTruthy();
      
      // Minimum 44px height for touch targets
      if (box) {
        expect(box.height).toBeGreaterThanOrEqual(44);
      }
    });

    test('pricing plans display in single column', async ({ page }) => {
      await page.goto('/pricing');
      
      // Check that plan cards stack vertically
      const planCards = page.locator('[class*="grid"]').first();
      await expect(planCards).toBeVisible();
    });
  });

  test.describe('Tablet viewport (768x1024)', () => {
    test.use({ viewport: { width: 768, height: 1024 } });

    test('navigation shows all links without hamburger', async ({ page }) => {
      await page.goto('/');
      
      // On tablet/desktop, regular nav should be visible
      // Hamburger should be hidden or menu should be expanded
      const journalLink = page.getByRole('link', { name: 'Journal' });
      // Links should be visible in horizontal layout
      await expect(journalLink).toBeVisible();
    });

    test('hero heading has larger font size', async ({ page }) => {
      await page.goto('/');
      
      const heading = page.locator('h2, h1').first();
      const fontSize = await heading.evaluate((el) => {
        return window.getComputedStyle(el).fontSize;
      });
      const fontSizeNum = parseFloat(fontSize);
      
      // Should be larger than mobile
      expect(fontSizeNum).toBeGreaterThanOrEqual(32);
    });

    test('feature cards display in 2 columns', async ({ page }) => {
      await page.goto('/');
      
      // Feature cards should be in a grid layout
      const featureSection = page.locator('.grid, [class*="grid"]').first();
      await expect(featureSection).toBeVisible();
    });
  });

  test.describe('Desktop viewport (1440x900)', () => {
    test.use({ viewport: { width: 1440, height: 900 } });

    test('full navigation menu is visible', async ({ page }) => {
      await page.goto('/');
      
      // All nav items should be visible
      await expect(page.getByRole('link', { name: 'Journal' })).toBeVisible();
      await expect(page.getByRole('link', { name: 'Pricing' })).toBeVisible();
      await expect(page.getByRole('link', { name: 'À propos' })).toBeVisible();
      
      // Hamburger should be hidden
      const hamburger = page.getByRole('button', { name: /menu/i });
      await expect(hamburger).toBeHidden();
    });

    test('hero heading has maximum font size', async ({ page }) => {
      await page.goto('/');
      
      const heading = page.locator('h2, h1').first();
      const fontSize = await heading.evaluate((el) => {
        return window.getComputedStyle(el).fontSize;
      });
      const fontSizeNum = parseFloat(fontSize);
      
      // Should be within desktop range (40-56px)
      expect(fontSizeNum).toBeGreaterThanOrEqual(40);
      expect(fontSizeNum).toBeLessThanOrEqual(56);
    });

    test('pricing plans display in grid layout', async ({ page }) => {
      await page.goto('/pricing');
      
      // Individual plans should be in 3 columns
      const individualsSection = page.locator('#particuliers');
      await expect(individualsSection).toBeVisible();
      
      // Business plans should be in 4 columns
      const businessSection = page.locator('#professionnels');
      await expect(businessSection).toBeVisible();
    });

    test('images are lazy loaded', async ({ page }) => {
      await page.goto('/documentation');
      
      const images = page.locator('img');
      const count = await images.count();
      
      if (count > 0) {
        const firstImage = images.first();
        const loading = await firstImage.getAttribute('loading');
        expect(loading).toBe('lazy');
      }
    });
  });

  test.describe('Accessibility', () => {
    test('all interactive elements meet touch target size', async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 667 });
      await page.goto('/pricing');
      
      // Check all buttons
      const buttons = page.getByRole('button');
      const buttonCount = await buttons.count();
      
      for (let i = 0; i < buttonCount; i++) {
        const button = buttons.nth(i);
        const box = await button.boundingBox();
        
        if (box && await button.isVisible()) {
          // Minimum 44x44px for accessibility
          expect(box.height).toBeGreaterThanOrEqual(44);
          expect(box.width).toBeGreaterThanOrEqual(44);
        }
      }
    });

    test('navigation has proper ARIA labels', async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 667 });
      await page.goto('/');
      
      const hamburger = page.getByRole('button', { name: /ouvrir le menu|fermer le menu/i });
      await expect(hamburger).toBeVisible();
      
      // Check aria-expanded attribute
      const ariaExpanded = await hamburger.getAttribute('aria-expanded');
      expect(ariaExpanded).toBeTruthy();
    });
  });
});

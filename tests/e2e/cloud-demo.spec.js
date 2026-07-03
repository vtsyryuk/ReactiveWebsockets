import { expect, test } from "@playwright/test";

test.describe.configure({ mode: "serial" });

test("loads the cloud demo UI and topics", async ({ page }) => {
  await page.goto("/");

  await expect(page).toHaveTitle(/ReactiveWebsockets demo/);
  await expect(page.getByRole("heading", { name: "ReactiveWebsockets demo" })).toBeVisible();
  await expect(page.locator("#status")).toContainText("Loaded 4 topics");
  await expect(page.locator("#topic")).toContainText("prices");
});

test("subscribes a client and publishes a routed reply", async ({ page }) => {
  await page.goto("/");

  await page.locator("#client").fill("alice");
  await page.locator("#topic").selectOption("prices");
  await page.locator("#content").fill("price=42.10");

  await page.getByRole("button", { name: "Subscribe" }).click();
  await expect(page.locator("#status")).toContainText("subscribed");
  await expect(page.locator("#state")).toContainText('"command": "subscribe"');
  await expect(page.locator("#state")).toContainText('"client": "alice"');

  await page.getByRole("button", { name: "Publish reply" }).click();
  await expect(page.locator("#status")).toContainText("published");
  await expect(page.locator("#state")).toContainText("price=42.10");

  await page.getByRole("button", { name: "Unsubscribe last" }).click();
  await expect(page.locator("#status")).toContainText("unsubscribed");
  await expect(page.locator("#state")).toContainText('"command": "unsubscribe"');
});

test("simulates multiple clients sharing one upstream topic", async ({ page }) => {
  await page.goto("/");

  await page.locator("#topic").selectOption("alerts");
  await page.getByRole("button", { name: "Simulate shared topic" }).click();

  await expect(page.locator("#status")).toContainText("simulated");
  await expect(page.locator("#output")).toContainText('"clients": 3');
  await expect(page.locator("#output")).toContainText('"command": "subscribe"');
  await expect(page.locator("#output")).toContainText('"command": "unsubscribe"');
});

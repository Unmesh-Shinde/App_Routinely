import {
	env,
	createExecutionContext,
	waitOnExecutionContext,
	SELF,
} from "cloudflare:test";
import { describe, it, expect } from "vitest";
import worker, { createMealNutritionPrompt, hasCompleteMealMacros } from "../src/index";

// For now, you'll need to do something like this to get a correctly-typed
// `Request` to pass to `worker.fetch()`.
const IncomingRequest = Request<unknown, IncomingRequestCfProperties>;

describe("Routinely AI backend", () => {
	it("instructs meal estimates to derive micronutrients from that meal's ingredients and portion", () => {
		const prompt = createMealNutritionPrompt("Grilled salmon", "120 g salmon with vegetables");
		expect(prompt).toContain("meal_title: \"Grilled salmon\"");
		expect(prompt).toContain("portion_details: \"120 g salmon with vegetables\"");
		expect(prompt).toContain("scale every nutrient");
		expect(prompt).toContain("Never reuse fixed nutrient values");
		expect(prompt).toContain("Omega-3, Vitamin D, Vitamin B6, Vitamin B12, Magnesium, or Zinc");
		expect(prompt).not.toContain("\\\"Vitamin D\\\":\\\"1.5 mcg\\\"");
	});

	it("rejects meal results with any missing core macro", () => {
		expect(hasCompleteMealMacros(20, 10, 5)).toBe(true);
		expect(hasCompleteMealMacros(0, 10, 5)).toBe(false);
		expect(hasCompleteMealMacros(20, 0, 5)).toBe(false);
		expect(hasCompleteMealMacros(20, 10, 0)).toBe(false);
	});

	it("returns a health response (unit style)", async () => {
		const request = new IncomingRequest("http://example.com/health");
		// Create an empty context to pass to `worker.fetch()`.
		const ctx = createExecutionContext();
		const response = await worker.fetch(request, env, ctx);
		// Wait for all `Promise`s passed to `ctx.waitUntil()` to settle before running test assertions
		await waitOnExecutionContext(ctx);
		expect(response.status).toBe(200);
		expect(await response.json()).toEqual({
			status: "ok",
			service: "routinely-ai-backend",
		});
	});

	it("returns 404 for an unknown path (integration style)", async () => {
		const response = await SELF.fetch("https://example.com/unknown");
		expect(response.status).toBe(404);
		expect(await response.json()).toEqual({ error: "not_found" });
	});

	it("does not process requests without Firebase configuration", async () => {
		const request = new IncomingRequest("http://example.com/v1/ai/meal-calories", {
			method: "POST",
			headers: { "content-type": "application/json" },
			body: JSON.stringify({ title: "Dal and rice", description: "One bowl" }),
		});
		const response = await worker.fetch(request, {} as Env, createExecutionContext());
		expect(response.status).toBe(503);
		expect(await response.json()).toEqual({ error: "firebase_not_configured" });
	});

	it("requires Firebase Auth and App Check credentials", async () => {
		const request = new IncomingRequest("http://example.com/v1/ai/meal-calories", {
			method: "POST",
			headers: { "content-type": "application/json" },
			body: JSON.stringify({ title: "Dal and rice", description: "One bowl" }),
		});
		const response = await worker.fetch(request, env, createExecutionContext());
		expect(response.status).toBe(401);
		expect(await response.json()).toEqual({ error: "unauthorized" });
	});
});

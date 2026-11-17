import {
	env,
	createExecutionContext,
	waitOnExecutionContext,
	SELF,
} from "cloudflare:test";
import { afterEach, describe, expect, it, vi } from "vitest";
import worker, {
	_callGemini,
	createMealNutritionPrompt,
	_getGeminiApiKeys,
	_getPacificDate,
	_getNextPacificMidnight,
	_getNextUtcMidnight,
	_getUtcDate,
} from "../src/index";

// For now, you'll need to do something like this to get a correctly-typed
// `Request` to pass to `worker.fetch()`.
const IncomingRequest = Request<unknown, IncomingRequestCfProperties>;

function createBudgetBinding(): DurableObjectNamespace {
	const retryCounts = new Map<string, number>();
	const providerCounts = new Map<string, number>();
	const exhaustedPairs = new Set<string>();
	return {
		idFromName: () => ({}) as DurableObjectId,
		get: () => ({
			fetch: async (_input: RequestInfo | URL, init?: RequestInit) => {
				const body = JSON.parse(String(init?.body ?? "{}"));
				let result: { allowed: boolean; used?: number };
				if (body.action === "reserve_retry") {
					const key = `${body.uid}|${body.category}|${body.day}`;
					const used = retryCounts.get(key) ?? 0;
					result = { allowed: used < body.limit, used };
					if (result.allowed) retryCounts.set(key, used + 1);
				} else if (body.action === "reserve_provider_attempt") {
					const used = providerCounts.get(body.day) ?? 0;
					result = { allowed: used < body.limit, used };
					if (result.allowed) providerCounts.set(body.day, used + 1);
				} else if (body.action === "is_pair_exhausted") {
					result = { allowed: !exhaustedPairs.has(`${body.day}|${body.pairId}`) };
				} else {
					exhaustedPairs.add(`${body.day}|${body.pairId}`);
					result = { allowed: true };
				}
				return Response.json(result);
			},
		}) as DurableObjectStub,
	} as unknown as DurableObjectNamespace;
}

describe("Routinely AI backend", () => {
	it("instructs meal estimates to allow zero macros and leave unsupported nutrient maps empty", () => {
		const prompt = createMealNutritionPrompt("Grilled salmon", "120 g salmon with vegetables");
		expect(prompt).toContain("meal_title: \"Grilled salmon\"");
		expect(prompt).toContain("portion_details: \"120 g salmon with vegetables\"");
		expect(prompt).toContain("scale every nutrient");
		expect(prompt).toContain("Never reuse fixed nutrient values");
		expect(prompt).toContain("A zero value is valid");
		expect(prompt).toContain("return an empty object");
		expect(prompt).toContain("do not invent values");
		expect(prompt).not.toContain("\\\"Vitamin D\\\":\\\"1.5 mcg\\\"");
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
		expect(await response.json()).toEqual({
			error: "not_found",
			errorCode: "not_found",
			scope: "request",
			upstreamStatus: null,
			retryable: false,
			message: "not found",
		});
	});

	it("does not process requests without Firebase configuration", async () => {
		const request = new IncomingRequest("http://example.com/v1/ai/meal-calories", {
			method: "POST",
			headers: { "content-type": "application/json" },
			body: JSON.stringify({ title: "Dal and rice", description: "One bowl" }),
		});
		const response = await worker.fetch(request, {} as Env, createExecutionContext());
		expect(response.status).toBe(503);
		expect(await response.json()).toEqual({
			error: "firebase_not_configured",
			errorCode: "firebase_not_configured",
			scope: "project",
			upstreamStatus: null,
			retryable: false,
			message: "firebase not configured",
		});
	});

	it("requires Firebase Auth and App Check credentials", async () => {
		const request = new IncomingRequest("http://example.com/v1/ai/meal-calories", {
			method: "POST",
			headers: { "content-type": "application/json" },
			body: JSON.stringify({ title: "Dal and rice", description: "One bowl" }),
		});
		const response = await worker.fetch(request, env, createExecutionContext());
		expect(response.status).toBe(401);
		expect(await response.json()).toEqual({
			error: "unauthorized",
			errorCode: "unauthorized",
			scope: "firebase",
			upstreamStatus: null,
			retryable: false,
			message: "unauthorized",
		});
	});

	it("atomically enforces eight daily retry reservations per user and category", async () => {
		const id = env.NUTRITION_BUDGET.idFromName(`retry-budget-test-${crypto.randomUUID()}`);
		const stub = env.NUTRITION_BUDGET.get(id);
		const day = "2099-03-04";
		const reservations = await Promise.all(Array.from({ length: 20 }, () => stub.fetch("https://budget.test/", {
			method: "POST",
			headers: { "content-type": "application/json" },
			body: JSON.stringify({ action: "reserve_retry", uid: "hashed-test-user", category: "breakfast", day, limit: 8 }),
		})));
		const results = await Promise.all(reservations.map((response) => response.json<{ allowed: boolean }>()));
		expect(results.filter((result) => result.allowed)).toHaveLength(8);

		const lunch = await stub.fetch("https://budget.test/", {
			method: "POST",
			headers: { "content-type": "application/json" },
			body: JSON.stringify({ action: "reserve_retry", uid: "hashed-test-user", category: "lunch", day, limit: 8 }),
		});
		expect(await lunch.json()).toMatchObject({ allowed: true, used: 1 });
	});

	it("uses server UTC dates and returns the next UTC budget reset", () => {
		const beforeMidnight = new Date("2026-11-17T23:59:59.000Z");
		expect(_getUtcDate(beforeMidnight)).toBe("2026-11-17");
		expect(new Date(_getNextUtcMidnight(beforeMidnight.getTime())).toISOString()).toBe("2026-11-18T00:00:00.000Z");
	});

	it("returns next Pacific midnight correctly across the daylight-saving transition", () => {
		const now = new Date("2026-11-01T07:30:00.000Z").getTime(); // 12:30 AM PDT, before fallback
		expect(_getPacificDate(new Date(now))).toBe("2026-11-01");
		expect(new Date(_getNextPacificMidnight(now)).toISOString()).toBe("2026-11-02T08:00:00.000Z");
	});
});

describe("Gemini API Key Selection and Quota Exhaustion", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("tries Gemini 3.5 Flash-Lite, 3.1 Flash-Lite, then 3.6 Flash before succeeding on the third model", async () => {
		const requestedModels: string[] = [];
		vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
			const url = new URL(String(input));
			const model = url.pathname.match(/models\/([^/]+):generateContent/)?.[1];
			expect(model).toBeDefined();
			requestedModels.push(model!);
			const requestBody = JSON.parse(String(init?.body ?? "{}"));
			expect(requestBody.generationConfig).not.toHaveProperty("temperature");

			if (model !== "gemini-3.6-flash") {
				return new Response(JSON.stringify({ error: { message: "model not found" } }), { status: 404 });
			}

			return new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: "ok" }] } }] }), {
				status: 200,
				headers: { "content-type": "application/json" },
			});
		}));

		const result = await _callGemini("test prompt", {
			GEMINI_API_KEYS_SET1: "test-key",
			NUTRITION_BUDGET: createBudgetBinding(),
		} as unknown as Env);

		expect(requestedModels).toEqual([
			"gemini-3.5-flash-lite",
			"gemini-3.5-flash-lite",
			"gemini-3.5-flash-lite",
			"gemini-3.1-flash-lite",
			"gemini-3.1-flash-lite",
			"gemini-3.1-flash-lite",
			"gemini-3.6-flash",
		]);
		expect(result.ok).toBe(true);
		expect(result.attempts.map((attempt) => attempt.endpoint)).toEqual(requestedModels);
	});

	it("stops before issuing a Gemini call when the shared upstream-attempt cap is reached", async () => {
		let upstreamCalls = 0;
		vi.stubGlobal("fetch", vi.fn(async () => {
			upstreamCalls += 1;
			return new Response(JSON.stringify({ error: { message: "temporary failure" } }), { status: 500 });
		}));
		const result = await _callGemini("test prompt", {
			GEMINI_API_KEYS_SET1: "budget-cap-key",
			GEMINI_MAX_UPSTREAM_ATTEMPTS_PER_DAY: "1",
			NUTRITION_BUDGET: createBudgetBinding(),
		} as unknown as Env);

		expect(upstreamCalls).toBe(1);
		expect(result.ok).toBe(false);
		expect(result.budgetLimited).toBe(true);
		expect(result.rawText).toBe("ai_provider_daily_budget_reached");
		expect(result.retryAt).toBeGreaterThan(Date.now());
	});

	it("persists a 429 key/model skip for the rest of the provider day", async () => {
		const primaryModel = "gemini-3.5-flash-lite";
		const requestedModels: string[] = [];
		vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
			const model = new URL(String(input)).pathname.match(/models\/([^/]+):generateContent/)?.[1] ?? "";
			requestedModels.push(model);
			if (requestedModels.length === 1) {
				return new Response(JSON.stringify({ error: { status: "RESOURCE_EXHAUSTED", message: "quota exceeded" } }), { status: 429 });
			}
			return new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: "ok" }] } }] }), { status: 200 });
		}));
		const retryEnv = {
			...env,
			GEMINI_API_KEYS_SET1: `429-persist-${crypto.randomUUID()}`,
			GEMINI_MAX_UPSTREAM_ATTEMPTS_PER_DAY: "300",
		};

		const first = await _callGemini("test prompt", retryEnv);
		expect(first.ok).toBe(true);
		const primaryCallsBeforeSecondRequest = requestedModels.filter((model) => model === primaryModel).length;
		const second = await _callGemini("test prompt", retryEnv);

		expect(second.ok).toBe(true);
		expect(requestedModels.filter((model) => model === primaryModel)).toHaveLength(primaryCallsBeforeSecondRequest);
	});

	it("retrieves keys correctly from sets 1 to 5", () => {
		const mockEnv = {
			GEMINI_API_KEYS_SET1: "key1,key2",
			GEMINI_API_KEYS_SET3: "key3",
			GEMINI_API_KEYS_SET5: "key4,key5,key6",
		} as Record<string, string | undefined>;

		const keys = _getGeminiApiKeys(mockEnv);
		expect(keys).toHaveLength(6);
		expect(keys[0]).toEqual({ key: "key1", label: "Api1Set1" });
		expect(keys[1]).toEqual({ key: "key2", label: "Api2Set1" });
		expect(keys[2]).toEqual({ key: "key3", label: "Api1Set3" });
		expect(keys[5]).toEqual({ key: "key6", label: "Api3Set5" });
	});

	it("persists exhausted key/model pairs by provider day", async () => {
		const stub = env.NUTRITION_BUDGET.get(env.NUTRITION_BUDGET.idFromName(`pair-state-test-${crypto.randomUUID()}`));
		const body = (action: string) => JSON.stringify({ action, day: "2099-03-04", pairId: "sha256-test-pair" });
		await stub.fetch("https://budget.test/", {
			method: "POST", headers: { "content-type": "application/json" }, body: body("mark_pair_exhausted"),
		});
		const exhausted = await stub.fetch("https://budget.test/", {
			method: "POST", headers: { "content-type": "application/json" }, body: body("is_pair_exhausted"),
		});
		expect(await exhausted.json()).toEqual({ allowed: false });
		const nextDay = await stub.fetch("https://budget.test/", {
			method: "POST", headers: { "content-type": "application/json" },
			body: JSON.stringify({ action: "is_pair_exhausted", day: "2099-03-05", pairId: "sha256-test-pair" }),
		});
		expect(await nextDay.json()).toEqual({ allowed: true });
	});
});

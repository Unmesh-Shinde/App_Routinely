/**
 * Welcome to Cloudflare Workers! This is your first worker.
 *
 * - Run `npm run dev` in your terminal to start a development server
 * - Open a browser tab at http://localhost:8787/ to see your worker in action
 * - Run `npm run deploy` to publish your worker
 *
 * Bind resources to your worker in `wrangler.jsonc`. After adding bindings, a type definition for the
 * `Env` object can be regenerated with `npm run cf-typegen`.
 *
 * Learn more at https://developers.cloudflare.com/workers/
 */

export default {
	async fetch(request, env, ctx): Promise<Response> {
		const url = new URL(request.url);

		if (request.method === "GET" && url.pathname === "/health") {
			return Response.json({
				status: "ok",
				service: "routinely-ai-backend",
			});
		}

		if (request.method === "GET" && url.pathname === "/") {
			return new Response("Routinely AI backend");
		}

		if (url.pathname === "/v1/ai/meal-calories") {
			const security = await authenticateRequest(request, env);
			if (security instanceof Response) {
				return security;
			}
			return handleMealCalories(request, env, security.uid);
		}

		if (url.pathname === "/v1/ai/workout-met") {
			const security = await authenticateRequest(request, env);
			if (security instanceof Response) {
				return security;
			}
			return handleWorkoutMet(request, env, security.uid);
		}

		return Response.json(
			{ error: "not_found" },
			{ status: 404 },
		);
	},
} satisfies ExportedHandler<Env>;

type FirebaseToken = {
		aud?: unknown;
		auth_time?: unknown;
		exp?: unknown;
		iat?: unknown;
		iss?: unknown;
		sub?: unknown;
	};

type FirebaseJwk = JsonWebKey & { kid?: string; alg?: string; kty?: string; use?: string };
type FirebaseJwkSet = { keys?: FirebaseJwk[] };
type CachedJwkSet = { value: FirebaseJwkSet; expiresAt: number };

const AUTH_JWKS_URL =
	"https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
const APP_CHECK_JWKS_URL = "https://firebaseappcheck.googleapis.com/v1/jwks";
const MAX_CLOCK_SKEW_SECONDS = 60;
const MAX_JWKS_CACHE_SECONDS = 6 * 60 * 60;
// Keep the primary and fallback on currently supported stable Flash models.
const GEMINI_MODEL = "gemini-3.6-flash";
const GEMINI_FALLBACK_MODEL = "gemini-3.5-flash";
const GEMINI_ATTEMPTS_PER_MODEL = 3;
const GEMINI_REQUEST_TIMEOUT_MS = 20_000;
const GEMINI_RETRY_DELAYS_MS = [250, 750];
const jwksCache = new Map<string, CachedJwkSet>();

async function authenticateRequest(
	request: Request,
	env: Env,
): Promise<{ uid: string } | Response> {
	if (!env.FIREBASE_PROJECT_ID || !env.FIREBASE_PROJECT_NUMBER || !env.FIREBASE_APP_ID) {
		return Response.json({ error: "firebase_not_configured" }, { status: 503 });
	}

	const authorization = request.headers.get("authorization") ?? "";
	const authMatch = /^Bearer\s+(.+)$/i.exec(authorization);
	const appCheckToken = request.headers.get("x-firebase-appcheck")?.trim();
	if (!authMatch || !appCheckToken) {
		return Response.json({ error: "unauthorized" }, { status: 401 });
	}

	try {
		const authToken = await verifyFirebaseJwt(authMatch[1], {
			jwksUrl: AUTH_JWKS_URL,
			issuer: `https://securetoken.google.com/${env.FIREBASE_PROJECT_ID}`,
			audience: env.FIREBASE_PROJECT_ID,
		});
		const appCheckTokenPayload = await verifyFirebaseJwt(appCheckToken, {
			jwksUrl: APP_CHECK_JWKS_URL,
			issuer: `https://firebaseappcheck.googleapis.com/${env.FIREBASE_PROJECT_NUMBER}`,
			audience: `projects/${env.FIREBASE_PROJECT_NUMBER}`,
		});

		if (appCheckTokenPayload.sub !== env.FIREBASE_APP_ID) {
			return Response.json({ error: "unauthorized" }, { status: 401 });
		}

		return { uid: authToken.sub as string };
	} catch {
		return Response.json({ error: "unauthorized" }, { status: 401 });
	}
}

async function verifyFirebaseJwt(
	token: string,
	options: { jwksUrl: string; issuer: string; audience: string },
): Promise<FirebaseToken & { sub: string }> {
	const parts = token.split(".");
	if (parts.length !== 3) {
		throw new Error("invalid_jwt");
	}

	const header = decodeJson<{ alg?: unknown; kid?: unknown; typ?: unknown }>(parts[0]);
	const payload = decodeJson<FirebaseToken>(parts[1]);
	if (header.alg !== "RS256" || header.typ !== "JWT" || typeof header.kid !== "string") {
		throw new Error("invalid_jwt_header");
	}

	const signingInput = new TextEncoder().encode(`${parts[0]}.${parts[1]}`);
	const signature = decodeBase64Url(parts[2]);
	let verified = false;

	for (let attempt = 0; attempt < 2 && !verified; attempt += 1) {
		const jwks = await getJwkSet(options.jwksUrl, attempt === 1);
		const jwk = jwks.keys?.find((key) => key.kid === header.kid);
		if (!jwk) {
			continue;
		}

		const publicKey = await crypto.subtle.importKey(
			"jwk",
			jwk,
			{ name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
			false,
			["verify"],
		);
		verified = await crypto.subtle.verify(
			{ name: "RSASSA-PKCS1-v1_5" },
			publicKey,
			signature,
			signingInput,
		);
	}

	if (!verified || !validFirebaseClaims(payload, options)) {
		throw new Error("invalid_firebase_token");
	}

	return payload as FirebaseToken & { sub: string };
}

async function getJwkSet(url: string, forceRefresh: boolean): Promise<FirebaseJwkSet> {
	const now = Date.now();
	const cached = jwksCache.get(url);
	if (!forceRefresh && cached && cached.expiresAt > now) {
		return cached.value;
	}

	const response = await fetch(url);
	if (!response.ok) {
		throw new Error("jwks_unavailable");
	}

	const value = await response.json() as FirebaseJwkSet;
	if (!Array.isArray(value.keys) || value.keys.length === 0) {
		throw new Error("invalid_jwks");
	}

	const maxAgeMatch = response.headers.get("cache-control")?.match(/max-age=(\d+)/i);
	const cacheSeconds = Math.min(
		maxAgeMatch ? Number(maxAgeMatch[1]) : 60 * 60,
		MAX_JWKS_CACHE_SECONDS,
	);
	jwksCache.set(url, { value, expiresAt: now + cacheSeconds * 1000 });
	return value;
}

function validFirebaseClaims(
	payload: FirebaseToken,
	options: { issuer: string; audience: string },
): boolean {
	const now = Math.floor(Date.now() / 1000);
	const exp = typeof payload.exp === "number" ? payload.exp : 0;
	const iat = typeof payload.iat === "number" ? payload.iat : 0;
	const authTime = typeof payload.auth_time === "number" ? payload.auth_time : 0;
	const subject = typeof payload.sub === "string" ? payload.sub : "";

	const audienceMatches = typeof payload.aud === "string"
		? payload.aud === options.audience
		: Array.isArray(payload.aud) && payload.aud.includes(options.audience);

	return payload.iss === options.issuer
		&& audienceMatches
		&& exp > now - MAX_CLOCK_SKEW_SECONDS
		&& iat <= now + MAX_CLOCK_SKEW_SECONDS
		&& authTime <= now + MAX_CLOCK_SKEW_SECONDS
		&& subject.length > 0
		&& subject.length <= 128;
}

function decodeJson<T>(value: string): T {
	try {
		return JSON.parse(new TextDecoder().decode(decodeBase64Url(value))) as T;
	} catch {
		throw new Error("invalid_jwt_encoding");
	}
}

function decodeBase64Url(value: string): Uint8Array {
	const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
	const padded = normalized + "=".repeat((4 - (normalized.length % 4)) % 4);
	const binary = atob(padded);
	return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

function parseCommaKeys(raw?: string): string[] {
	if (!raw) return [];
	return raw
		.split(",")
		.map((k) => k.trim().replace(/^["']|["']$/g, ""))
		.filter(Boolean);
}

function extractNumber(val: unknown): number {
	if (typeof val === "number") return Number.isFinite(val) ? val : 0;
	if (typeof val === "string") {
		const match = val.match(/(\d+(?:\.\d+)?)/);
		if (match) {
			const parsed = parseFloat(match[1]);
			return Number.isFinite(parsed) ? parsed : 0;
		}
	}
	return 0;
}

type GeminiApiKeySlot = { key: string; label: string };

function getGeminiApiKeys(env: Record<string, string | undefined>): GeminiApiKeySlot[] {
	const proKeys: GeminiApiKeySlot[] = [];
	const freeKeys: GeminiApiKeySlot[] = [];
	const seenKeys = new Set<string>();

	// 1. Check GEMINI_PRO_API_KEYS (comma-separated or single string)
	for (const [index, key] of parseCommaKeys(env.GEMINI_PRO_API_KEYS).entries()) {
		if (!seenKeys.has(key)) {
			proKeys.push({ key, label: `pro-${index + 1}` });
			seenKeys.add(key);
		}
	}

	// 2. Check GEMINI_FREE_API_KEYS (comma-separated or single string)
	for (const [index, key] of parseCommaKeys(env.GEMINI_FREE_API_KEYS).entries()) {
		if (!seenKeys.has(key)) {
			freeKeys.push({ key, label: `free-${index + 1}` });
			seenKeys.add(key);
		}
	}

	// 3. Fallback: check legacy GEMINI_API_KEY_1..3 and GEMINI_API_KEY for backward compatibility
	for (const keyVar of ["GEMINI_API_KEY_1", "GEMINI_API_KEY_2", "GEMINI_API_KEY_3", "GEMINI_API_KEY"]) {
		const raw = env[keyVar];
		if (raw) {
			const cleaned = raw.trim().replace(/^["']|["']$/g, "");
			if (cleaned && !seenKeys.has(cleaned)) {
				freeKeys.push({ key: cleaned, label: `legacy-${keyVar}` });
				seenKeys.add(cleaned);
			}
		}
	}

	return [...proKeys, ...freeKeys];
}

async function handleMealCalories(request: Request, env: Env, uid: string): Promise<Response> {
	if (request.method !== "POST") {
		return Response.json({ error: "method_not_allowed" }, { status: 405 });
	}

	const apiKeys = getGeminiApiKeys(env as unknown as Record<string, string | undefined>);
	if (apiKeys.length === 0) {
		return Response.json({ error: "ai_provider_not_configured" }, { status: 503 });
	}

	let payload: { title?: unknown; description?: unknown };
	try {
		payload = await request.json();
	} catch {
		return Response.json({ error: "invalid_json" }, { status: 400 });
	}

	const title = typeof payload.title === "string" ? payload.title.trim() : "";
	const description = typeof payload.description === "string" ? payload.description.trim() : "";
	if (!title || title.length > 500 || description.length > 2_000) {
		return Response.json({ error: "invalid_request" }, { status: 400 });
	}

	const prompt = createMealNutritionPrompt(title, description);
	void uid;

	const stringValue = { type: "STRING" };
	const mealSchema = {
		type: "OBJECT",
		properties: {
			total_calories: { type: "INTEGER" },
			carbs_g: { type: "NUMBER" },
			protein_g: { type: "NUMBER" },
			fat_g: { type: "NUMBER" },
			fiber_g: { type: "NUMBER" },
			fat_breakdown: {
				type: "OBJECT",
				properties: {
					"Saturated Fat": stringValue,
					"Monounsaturated Fat": stringValue,
					"Polyunsaturated Fat": stringValue,
					"Omega-3": stringValue,
				},
			},
			carb_breakdown: {
				type: "OBJECT",
				properties: { "Net Carbs": stringValue, "Total Sugars": stringValue },
			},
			vitamins: {
				type: "OBJECT",
				properties: {
					"Vitamin A": stringValue,
					"Vitamin C": stringValue,
					"Vitamin D": stringValue,
					"Vitamin B12": stringValue,
					"Vitamin B6": stringValue,
					Folate: stringValue,
					Choline: stringValue,
				},
			},
			minerals: {
				type: "OBJECT",
				properties: {
					Calcium: stringValue,
					Iron: stringValue,
					Potassium: stringValue,
					Sodium: stringValue,
					Zinc: stringValue,
					Magnesium: stringValue,
					Selenium: stringValue,
					Phosphorus: stringValue,
				},
			},
			amino_acids: {
				type: "OBJECT",
				properties: {
					Leucine: stringValue,
					Lysine: stringValue,
				Valine: stringValue,
				Tryptophan: stringValue,
				Isoleucine: stringValue,
				Methionine: stringValue,
				Phenylalanine: stringValue,
				Threonine: stringValue,
				Histidine: stringValue,
			},
			},
			antioxidants: {
				type: "OBJECT",
				properties: {
					Polyphenols: stringValue,
				Lycopene: stringValue,
				Lutein: stringValue,
				},
			},
			other_nutrients: {
				type: "OBJECT",
				properties: {
					"Saturated Fat": stringValue,
					"Total Sugars": stringValue,
					Cholesterol: stringValue,
					"Omega-3": stringValue,
					"Food Water Content": stringValue,
				},
			},
		},
		required: [
			"total_calories",
			"carbs_g",
			"protein_g",
			"fat_g",
			"fiber_g",
			"fat_breakdown",
			"carb_breakdown",
			"vitamins",
			"minerals",
			"amino_acids",
			"antioxidants",
			"other_nutrients",
		],
	};

	const upstreamResult = await callGemini(prompt, env, mealSchema);

	if (!upstreamResult.ok) {
		return Response.json(
			{ error: providerErrorCode(upstreamResult.status, upstreamResult.rawText) },
			{ status: upstreamResult.status === 429 ? 429 : 502 },
		);
	}

	let providerResponse: GeminiResponse;
	try {
		providerResponse = JSON.parse(upstreamResult.rawText) as GeminiResponse;
	} catch {
		return Response.json({ error: "ai_invalid_response" }, { status: 502 });
	}

	const text = extractGeminiText(providerResponse);
	if (!text) {
		return Response.json({ error: "ai_empty_response" }, { status: 502 });
	}

	try {
		const jsonMatch = text.match(/\{[\s\S]*\}/);
		if (!jsonMatch) {
			return Response.json({ error: "ai_invalid_response" }, { status: 502 });
		}
		const result = JSON.parse(jsonMatch[0]);
		const rawCalories = extractNumber(result.total_calories ?? result.calories ?? result.totalCalories);
		const calories = Math.round(rawCalories);
		if (!Number.isInteger(calories) || calories <= 0 || calories > 20_000) {
			return Response.json({ error: "ai_invalid_response" }, { status: 502 });
		}

		const rawCarbs = extractNumber(result.carbs_g ?? result.carbs ?? result.carbohydrates ?? result.carbsG);
		const rawProtein = extractNumber(result.protein_g ?? result.protein ?? result.proteinG);
		const rawFat = extractNumber(result.fat_g ?? result.fat ?? result.fatG);
		const rawFiber = extractNumber(result.fiber_g ?? result.fiber ?? result.fiberG);

		if (!hasCompleteMealMacros(rawCarbs, rawProtein, rawFat)) {
			console.warn(`Gemini returned incomplete macros for calories=${calories} (carbs=${rawCarbs}, protein=${rawProtein}, fat=${rawFat}), rejecting with ai_empty_response.`);
			return Response.json({ error: "ai_empty_response" }, { status: 502 });
		}

		return Response.json({
			total_calories: calories,
			carbs_g: Math.max(0, Math.round(rawCarbs * 10.0) / 10.0),
			protein_g: Math.max(0, Math.round(rawProtein * 10.0) / 10.0),
			fat_g: Math.max(0, Math.round(rawFat * 10.0) / 10.0),
			fiber_g: Math.max(0, Math.round(rawFiber * 10.0) / 10.0),
			fat_breakdown: typeof result.fat_breakdown === "object" && result.fat_breakdown !== null ? result.fat_breakdown : {},
			carb_breakdown: typeof result.carb_breakdown === "object" && result.carb_breakdown !== null ? result.carb_breakdown : {},
			vitamins: typeof result.vitamins === "object" && result.vitamins !== null ? result.vitamins : {},
			minerals: typeof result.minerals === "object" && result.minerals !== null ? result.minerals : {},
			amino_acids: typeof result.amino_acids === "object" && result.amino_acids !== null ? result.amino_acids : {},
			antioxidants: typeof result.antioxidants === "object" && result.antioxidants !== null ? result.antioxidants : {},
			other_nutrients: typeof result.other_nutrients === "object" && result.other_nutrients !== null ? result.other_nutrients : {},
		});
	} catch {
		return Response.json({ error: "ai_invalid_response" }, { status: 502 });
	}
}

export function createMealNutritionPrompt(title: string, description: string): string {
	return [
		"Calculate nutritional breakdown for exactly one meal from these fields:",
		`meal_title: ${JSON.stringify(title)}`,
		`portion_details: ${JSON.stringify(description)}`,
		"",
		"Rules:",
		"- Treat meal_title and portion_details as the same meal, not two separate meals.",
		"- Treat every supplied gram value as final edible weight; use the stated serving size and ingredients to scale every nutrient.",
		"- Estimate each meal independently from its ingredients and portion. Never reuse fixed nutrient values from another meal or from the output example.",
		"- Do not use generic default values for Omega-3, Vitamin D, Vitamin B6, Vitamin B12, Magnesium, or Zinc. Estimate them from this meal's foods; use zero when ingredients provide negligible amounts.",
		"- Estimate total calories (kcal), macros in grams (carbs_g, protein_g, fat_g, fiber_g), key vitamins (Vitamin A, Vitamin C, Vitamin D, Vitamin B12, Vitamin B6, Folate, Choline), key minerals (Calcium, Iron, Potassium, Sodium, Zinc, Magnesium, Selenium, Phosphorus), essential amino acids (Leucine, Lysine, Valine, Tryptophan), antioxidants (Polyphenols, Lycopene, Lutein), and other nutrients (Saturated Fat, Total Sugars, Cholesterol, Omega-3, Food Water Content).",
		"- Output total_calories, carbs_g, protein_g, fat_g, and fiber_g strictly as raw numeric numbers (e.g. 450, 45.0, 25.0), NOT string values with units.",
		"- Populate nutrient maps with specific nutrient names as keys and values with appropriate units (for example, \"Vitamin C\": \"value mg\"). Do NOT use literal key names like \"name\".",
		"- Keep response concise and output strictly valid JSON matching the requested schema. Any example values are format hints only; calculate all values for this meal.",
	].join("\n");
}

export function hasCompleteMealMacros(carbs: number, protein: number, fat: number): boolean {
	return Number.isFinite(carbs) && carbs > 0 && Number.isFinite(protein) && protein > 0 && Number.isFinite(fat) && fat > 0;
}

async function handleWorkoutMet(request: Request, env: Env, uid: string): Promise<Response> {
	if (request.method !== "POST") {
		return Response.json({ error: "method_not_allowed" }, { status: 405 });
	}

	const apiKeys = getGeminiApiKeys(env as unknown as Record<string, string | undefined>);
	if (apiKeys.length === 0) {
		return Response.json({ error: "ai_provider_not_configured" }, { status: 503 });
	}

	let payload: Record<string, unknown>;
	try {
		payload = await request.json() as Record<string, unknown>;
	} catch {
		return Response.json({ error: "invalid_json" }, { status: 400 });
	}

	const stringField = (name: string, maxLength: number): string => {
		const value = typeof payload[name] === "string" ? payload[name] as string : "";
		return value.trim().slice(0, maxLength);
	};
	const numberField = (name: string): number => {
		const value = typeof payload[name] === "number" ? payload[name] as number : 0;
		return Number.isFinite(value) ? value : 0;
	};

	const name = stringField("exercise_name", 500);
	if (!name) {
		return Response.json({ error: "invalid_request" }, { status: 400 });
	}

	const prompt = [
		"Classify this workout and estimate a base MET value for calorie-burn calculation:",
		`exercise_name: ${JSON.stringify(name)}`,
		`target_area: ${JSON.stringify(stringField("target_area", 200))}`,
		`exercise_type: ${JSON.stringify(stringField("exercise_type", 200))}`,
		`effort_label: ${JSON.stringify(stringField("effort_label", 200))}`,
		`sets: ${numberField("sets")}`,
		`reps_or_duration: ${JSON.stringify(stringField("reps", 200))}`,
		`duration_seconds: ${numberField("duration_seconds")}`,
		`rest_seconds: ${numberField("rest_seconds")}`,
		`rounds: ${numberField("rounds")}`,
		`work_seconds: ${numberField("work_seconds")}`,
		`added_weight_kg: ${numberField("added_weight_kg")}`,
		`distance_km: ${numberField("distance_km")}`,
		`user_intensity_percent: ${numberField("intensity")}`,
		"",
		"Rules:",
		"- Return the base MET for the exercise type at normal/moderate effort.",
		"- Do not multiply by sets, reps, duration, body weight, or intensity.",
		"- Use accepted Compendium-style MET ranges when possible.",
		"- Keep MET between 1.0 and 15.0.",
		"Return only JSON in this exact shape: {\"exercise_family\": string, \"base_met\": number}.",
	].join("\n");
	void uid;

	const upstreamResult = await callGemini(prompt, env);

	if (!upstreamResult.ok) {
		return Response.json(
			{ error: providerErrorCode(upstreamResult.status, upstreamResult.rawText) },
			{ status: upstreamResult.status === 429 ? 429 : 502 },
		);
	}

	let providerResponse: GeminiResponse;
	try {
		providerResponse = JSON.parse(upstreamResult.rawText) as GeminiResponse;
	} catch {
		return Response.json({ error: "ai_invalid_response" }, { status: 502 });
	}

	const text = extractGeminiText(providerResponse);
	if (!text) {
		return Response.json({ error: "ai_empty_response" }, { status: 502 });
	}

	try {
		const result = JSON.parse(text.replace(/^```json\s*/i, "").replace(/\s*```$/, ""));
		const met = Number(result.base_met);
		if (!Number.isFinite(met) || met < 1 || met > 15) {
			return Response.json({ error: "ai_invalid_response" }, { status: 502 });
		}
		return Response.json({ base_met: met });
	} catch {
		return Response.json({ error: "ai_invalid_response" }, { status: 502 });
	}
}

function providerErrorCode(status: number, rawText = ""): string {
	if (isLocationRejected({ status, rawText })) return "ai_provider_location";
	if (isQuotaExhausted({ status, rawText })) return "ai_provider_quota";
	if (status === 401 || status === 403) return "ai_provider_auth_error";
	if (status === 429 || status === 500 || status === 503) return "ai_provider_busy";
	if (status === 400) return "ai_provider_bad_request";
	return "ai_provider_error";
}

type GeminiAttempt = {
	endpoint: string;
	keySlot: string;
	status: number;
	rawText: string;
	ok: boolean;
	durationMs: number;
};

type GeminiCallResult = {
	ok: boolean;
	status: number;
	rawText: string;
	attempts: GeminiAttempt[];
};

async function callGemini(prompt: string, env: Env, responseSchema?: Record<string, unknown>): Promise<GeminiCallResult> {
	const apiKeys = getGeminiApiKeys(env as unknown as Record<string, string | undefined>);
	if (apiKeys.length === 0) {
		return { ok: false, status: 503, rawText: "ai_provider_not_configured", attempts: [] };
	}

	const headers = {
		accept: "application/json",
		"content-type": "application/json",
	};

	const genConfig: Record<string, unknown> = {
		temperature: 0.1,
		responseMimeType: "application/json",
	};
	if (responseSchema) {
		genConfig.responseSchema = responseSchema;
	}

	const structuredPayload = JSON.stringify({
		contents: [{ parts: [{ text: prompt }] }],
		generationConfig: genConfig,
	});
	const compatibilityPayload = JSON.stringify({
		contents: [{ parts: [{ text: prompt }] }],
	});

	const attempts: GeminiAttempt[] = [];
	// Model-first approach: try best model across all keys (Pro keys first, then Free keys)
	// before moving to fallback model.
	const kind = responseSchema ? "meal" : "workout";
	for (const model of [GEMINI_MODEL, GEMINI_FALLBACK_MODEL]) {
		for (const keySlot of apiKeys) {
			let shouldTryCompatibility = false;
			let skipRemainingAttemptsForKey = false;

			for (const [payloadIndex, payload] of [structuredPayload, compatibilityPayload].entries()) {
				if (payloadIndex === 1 && !shouldTryCompatibility) break;
				if (skipRemainingAttemptsForKey) break;

				for (let attemptNumber = 0; attemptNumber < GEMINI_ATTEMPTS_PER_MODEL; attemptNumber += 1) {
					const attempt = await requestGeminiModel(model, keySlot, headers, payload);
					attempts.push(attempt);

					if (attempt.ok) {
						console.info(`Gemini ${kind}/${model} succeeded with ${keySlot.label}`, {
							keySlot: keySlot.label,
							status: attempt.status,
							durationMs: attempt.durationMs,
						});
						return { ok: true, status: attempt.status, rawText: attempt.rawText, attempts };
					}
					logAttemptFailure(kind, attempt);

					if (isLocationRejected(attempt)) {
						// The precondition may be specific to this key's project. Try the next configured key.
						// If the restriction is request-wide, later keys will return the same error and be logged.
						skipRemainingAttemptsForKey = true;
						break;
					}

					// If 429 Daily Quota / Resource Exhausted, skip remaining retries & payloads for this key
					// and move immediately (0ms delay) to the next key!
					if (isQuotaExhausted(attempt)) {
						skipRemainingAttemptsForKey = true;
						break;
					}

					if (attempt.status === 400) {
						shouldTryCompatibility = payloadIndex === 0;
						break;
					}

					if (!isRetryableProviderStatus(attempt.status) && !isModelUnavailable(attempt)) {
						break;
					}

					if (attemptNumber < GEMINI_ATTEMPTS_PER_MODEL - 1) {
						await wait(GEMINI_RETRY_DELAYS_MS[attemptNumber] ?? GEMINI_RETRY_DELAYS_MS.at(-1)!);
					}
				}
			}
		}
	}

	const lastAttempt = attempts.at(-1);
	return {
		ok: false,
		status: lastAttempt?.status ?? 502,
		rawText: lastAttempt?.rawText ?? "",
		attempts,
	};
}

async function requestGeminiModel(
	model: string,
	keySlot: GeminiApiKeySlot,
	headers: Record<string, string>,
	body: string,
): Promise<GeminiAttempt> {
	const startedAt = Date.now();
	const controller = new AbortController();
	const timeoutId = setTimeout(() => controller.abort(), GEMINI_REQUEST_TIMEOUT_MS);
	const endpoint = model;

	try {
		const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${encodeURIComponent(keySlot.key)}`;
		const response = await fetch(url, {
			method: "POST",
			headers,
			body,
			signal: controller.signal,
		});
		return {
			endpoint,
			keySlot: keySlot.label,
			status: response.status,
			rawText: await response.text(),
			ok: response.ok,
			durationMs: Date.now() - startedAt,
		};
	} catch (error) {
		return {
			endpoint,
			keySlot: keySlot.label,
			status: 0,
			rawText: error instanceof Error ? error.message : "request_failed",
			ok: false,
			durationMs: Date.now() - startedAt,
		};
	} finally {
		clearTimeout(timeoutId);
	}
}

function isRetryableProviderStatus(status: number): boolean {
	return status === 0 || status === 408 || status === 429 || status >= 500;
}

function isPermanentProviderFailure(attempt: Pick<GeminiAttempt, "status" | "rawText">): boolean {
	return isLocationRejected(attempt) || isQuotaExhausted(attempt);
}

function isLocationRejected(attempt: Pick<GeminiAttempt, "status" | "rawText">): boolean {
	return attempt.status === 400 && /location is not supported|failed_precondition/i.test(attempt.rawText);
}

function isQuotaExhausted(attempt: Pick<GeminiAttempt, "status" | "rawText">): boolean {
	return attempt.status === 429 && /quota|resource_exhausted|exceeded/i.test(attempt.rawText);
}

function isModelUnavailable(attempt: GeminiAttempt): boolean {
	return attempt.status === 404 || (attempt.status === 400 && /model/i.test(attempt.rawText));
}

function wait(milliseconds: number): Promise<void> {
	return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

type GeminiResponse = {
	candidates?: Array<{ content?: { parts?: Array<{ text?: string }> } }>;
};

function extractGeminiText(response: GeminiResponse): string {
	return response.candidates?.[0]?.content?.parts
		?.map((part) => part.text ?? "")
		.join("")
		.trim() ?? "";
}

function logAttemptFailure(kind: string, attempt: GeminiAttempt): void {
	let providerStatus: unknown;
	let providerCode: unknown;
	let providerMessage: string | undefined;

	try {
		const body = JSON.parse(attempt.rawText) as { error?: { status?: unknown; code?: unknown; message?: unknown } };
		providerStatus = body.error?.status;
		providerCode = body.error?.code;
		providerMessage = typeof body.error?.message === "string" ? body.error.message : undefined;
	} catch {
		providerMessage = attempt.rawText.slice(0, 300);
	}

	console.warn(`Gemini ${kind}/${attempt.endpoint} via ${attempt.keySlot} failed (HTTP ${attempt.status})`, {
		keySlot: attempt.keySlot,
		providerStatus,
		providerCode,
		providerMessage: providerMessage || attempt.rawText || "(empty response)",
		rawLength: attempt.rawText.length,
		durationMs: attempt.durationMs,
	});
}

function classifyProviderMessage(message: string): string {
	if (!message.trim()) return "empty_body";
	if (/api\s*key|apikey|permission|unauthorized/.test(message)) return "api_key_or_permission";
	if (/model|not found|unsupported/.test(message)) return "model";
	if (/billing|precondition|free tier|country|location/.test(message)) return "prerequisite";
	if (/invalid|request|parameter/.test(message)) return "invalid_request";
	return "provider_error";
}

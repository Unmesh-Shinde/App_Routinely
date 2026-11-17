import { DurableObject } from "cloudflare:workers";

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

		return apiErrorResponse("not_found", 404, { scope: "request" });
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
// User-selected Gemini cascade order.
const GEMINI_MODEL = "gemini-3.5-flash-lite";
const GEMINI_FALLBACK_MODEL = "gemini-3.1-flash-lite";
const GEMINI_SECONDARY_FALLBACK_MODEL = "gemini-3.6-flash";
const GEMINI_MODELS = [GEMINI_MODEL, GEMINI_FALLBACK_MODEL, GEMINI_SECONDARY_FALLBACK_MODEL];
const GEMINI_ATTEMPTS_PER_MODEL = 3;
const GEMINI_REQUEST_TIMEOUT_MS = 20_000;
const GEMINI_RETRY_DELAYS_MS = [250, 750];
const DEFAULT_GEMINI_UPSTREAM_ATTEMPT_LIMIT = 300;
const jwksCache = new Map<string, CachedJwkSet>();

type ApiErrorScope = "firebase" | "project" | "key" | "key_model" | "model" | "meal_input" | "request" | "provider" | "location" | "user_budget";

type BudgetRequest =
	| { action: "reserve_retry"; uid: string; category: string; day: string; limit: number }
	| { action: "reserve_provider_attempt"; day: string; limit: number }
	| { action: "is_pair_exhausted"; day: string; pairId: string }
	| { action: "mark_pair_exhausted"; day: string; pairId: string };

type BudgetResponse = { allowed: boolean; used?: number };

/** SQLite-backed, single-writer quotas shared across Worker isolates. */
export class NutritionBudgetDurableObject extends DurableObject<Env> {
	constructor(ctx: DurableObjectState, env: Env) {
		super(ctx, env);
		this.ctx.storage.sql.exec(`
			CREATE TABLE IF NOT EXISTS retry_budget (
				uid TEXT NOT NULL, category TEXT NOT NULL, day TEXT NOT NULL, used INTEGER NOT NULL DEFAULT 0,
				PRIMARY KEY (uid, category, day)
			);
			CREATE TABLE IF NOT EXISTS provider_budget (
				day TEXT PRIMARY KEY, used INTEGER NOT NULL DEFAULT 0
			);
			CREATE TABLE IF NOT EXISTS exhausted_pairs (
				day TEXT NOT NULL, pair_id TEXT NOT NULL, PRIMARY KEY (day, pair_id)
			);
		`);
		this.ctx.storage.sql.exec("DELETE FROM retry_budget WHERE day < date('now', '-45 days')");
		this.ctx.storage.sql.exec("DELETE FROM provider_budget WHERE day < date('now', '-45 days')");
		this.ctx.storage.sql.exec("DELETE FROM exhausted_pairs WHERE day < date('now', '-45 days')");
	}

	async fetch(request: Request): Promise<Response> {
		if (request.method !== "POST") return Response.json({ error: "method_not_allowed" }, { status: 405 });
		let input: BudgetRequest;
		try {
			input = await request.json() as BudgetRequest;
		} catch {
			return Response.json({ error: "invalid_json" }, { status: 400 });
		}
		const result = this.ctx.storage.transactionSync((): BudgetResponse => {
			if (input.action === "reserve_retry") {
				const row = this.ctx.storage.sql.exec<{ used: number }>(
					"SELECT used FROM retry_budget WHERE uid = ? AND category = ? AND day = ?",
					input.uid, input.category, input.day,
				).toArray()[0];
				const used = row?.used ?? 0;
				if (used >= input.limit) return { allowed: false, used };
				this.ctx.storage.sql.exec(
					"INSERT INTO retry_budget (uid, category, day, used) VALUES (?, ?, ?, 1) ON CONFLICT(uid, category, day) DO UPDATE SET used = used + 1",
					input.uid, input.category, input.day,
				);
				return { allowed: true, used: used + 1 };
			}
			if (input.action === "reserve_provider_attempt") {
				const row = this.ctx.storage.sql.exec<{ used: number }>(
					"SELECT used FROM provider_budget WHERE day = ?", input.day,
				).toArray()[0];
				const used = row?.used ?? 0;
				if (used >= input.limit) return { allowed: false, used };
				this.ctx.storage.sql.exec(
					"INSERT INTO provider_budget (day, used) VALUES (?, 1) ON CONFLICT(day) DO UPDATE SET used = used + 1",
					input.day,
				);
				return { allowed: true, used: used + 1 };
			}
			if (input.action === "is_pair_exhausted") {
				const row = this.ctx.storage.sql.exec<{ found: number }>(
					"SELECT 1 AS found FROM exhausted_pairs WHERE day = ? AND pair_id = ?", input.day, input.pairId,
				).toArray()[0];
				return { allowed: !row };
			}
			this.ctx.storage.sql.exec(
				"INSERT OR IGNORE INTO exhausted_pairs (day, pair_id) VALUES (?, ?)", input.day, input.pairId,
			);
			return { allowed: true };
		});
		return Response.json(result);
	}
}

async function budgetRequest(env: Env, input: BudgetRequest): Promise<BudgetResponse> {
	const id = env.NUTRITION_BUDGET.idFromName("routinely-nutrition-global-v1");
	const stub = env.NUTRITION_BUDGET.get(id);
	const response = await stub.fetch("https://nutrition-budget.internal/", {
		method: "POST",
		headers: { "content-type": "application/json" },
		body: JSON.stringify(input),
	});
	if (!response.ok) throw new Error(`Nutrition budget Durable Object returned ${response.status}`);
	return response.json<BudgetResponse>();
}

async function sha256Hex(value: string): Promise<string> {
	const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
	return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

function getProviderQuotaDate(): string {
	return _getPacificDate();
}

export function _getUtcDate(date = new Date()): string {
	return date.toISOString().slice(0, 10);
}

export function _getNextUtcMidnight(now = Date.now()): number {
	const date = new Date(now);
	return Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate() + 1);
}

export function _getNextPacificMidnight(now = Date.now()): number {
	const formatter = new Intl.DateTimeFormat("en-US", {
		timeZone: "America/Los_Angeles",
		year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit",
		hourCycle: "h23",
	});
	const parts = Object.fromEntries(formatter.formatToParts(new Date(now)).map((part) => [part.type, part.value]));
	const target = Date.UTC(Number(parts.year), Number(parts.month) - 1, Number(parts.day) + 1);
	let candidate = target;
	// Correct for the Pacific offset at the target instant, including DST changes.
	for (let attempt = 0; attempt < 3; attempt += 1) {
		const local = Object.fromEntries(formatter.formatToParts(new Date(candidate)).map((part) => [part.type, part.value]));
		const representedAsUtc = Date.UTC(
			Number(local.year), Number(local.month) - 1, Number(local.day),
			Number(local.hour), Number(local.minute), Number(local.second),
		);
		const difference = target - representedAsUtc;
		candidate += difference;
		if (difference === 0) break;
	}
	return candidate;
}

function apiErrorResponse(
	code: string,
	httpStatus: number,
	options: { scope: ApiErrorScope; upstreamStatus?: number; retryable?: boolean; message?: string; retryAt?: number },
): Response {
	return Response.json({
		error: code,
		errorCode: code,
		scope: options.scope,
		upstreamStatus: options.upstreamStatus ?? null,
		retryable: options.retryable ?? false,
		message: options.message ?? code.replaceAll("_", " "),
		...(options.retryAt !== undefined ? { retryAt: options.retryAt } : {}),
	}, { status: httpStatus });
}

async function authenticateRequest(
	request: Request,
	env: Env,
): Promise<{ uid: string } | Response> {
	if (!env.FIREBASE_PROJECT_ID || !env.FIREBASE_PROJECT_NUMBER || !env.FIREBASE_APP_ID) {
		return apiErrorResponse("firebase_not_configured", 503, { scope: "project" });
	}

	const authorization = request.headers.get("authorization") ?? "";
	const authMatch = /^Bearer\s+(.+)$/i.exec(authorization);
	const appCheckToken = request.headers.get("x-firebase-appcheck")?.trim();
	if (!authMatch || !appCheckToken) {
		return apiErrorResponse("unauthorized", 401, { scope: "firebase" });
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
			return apiErrorResponse("unauthorized", 401, { scope: "firebase" });
		}

		return { uid: authToken.sub as string };
	} catch {
		return apiErrorResponse("unauthorized", 401, { scope: "firebase" });
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

function extractOptionalNumber(val: unknown): number | null {
	if (typeof val === "number") return Number.isFinite(val) ? val : null;
	if (typeof val === "string") {
		const text = val.trim();
		if (!text) return null;
		const parsed = Number(text);
		return Number.isFinite(parsed) ? parsed : null;
	}
	return null;
}

export function _getPacificDate(dateValue = new Date()): string {
	const parts = new Intl.DateTimeFormat("en-US", {
		timeZone: "America/Los_Angeles",
		year: "numeric",
		month: "2-digit",
		day: "2-digit",
	}).formatToParts(dateValue);
	const date = Object.fromEntries(parts.map((part) => [part.type, part.value]));
	return `${date.year}-${date.month}-${date.day}`;
}

const keyModelId = (key: string, model: string) => `${key}\u0000${model}`;

export type GeminiApiKeySlot = { key: string; label: string };

export function _getGeminiApiKeys(env: Record<string, string | undefined>): GeminiApiKeySlot[] {
	const availableKeys: GeminiApiKeySlot[] = [];
	const seenKeys = new Set<string>();

	for (let setNum = 1; setNum <= 5; setNum++) {
		const envVarName = `GEMINI_API_KEYS_SET${setNum}`;
		const rawKeys = parseCommaKeys(env[envVarName]);

		for (const [index, key] of rawKeys.entries()) {
			if (!seenKeys.has(key)) {
				seenKeys.add(key);
				availableKeys.push({ key, label: `Api${index + 1}Set${setNum}` });
			}
		}
	}

	return availableKeys;
}

async function handleMealCalories(request: Request, env: Env, uid: string): Promise<Response> {
	if (request.method !== "POST") {
		return apiErrorResponse("method_not_allowed", 405, { scope: "request" });
	}

	const apiKeys = _getGeminiApiKeys(env as unknown as Record<string, string | undefined>);
	if (apiKeys.length === 0) {
		return apiErrorResponse("ai_provider_not_configured", 503, { scope: "project" });
	}

	let payload: {
		title?: unknown;
		description?: unknown;
		request_kind?: unknown;
		meal_category?: unknown;
	};
	try {
		payload = await request.json();
	} catch {
		return apiErrorResponse("invalid_json", 400, { scope: "request" });
	}

	const title = typeof payload.title === "string" ? payload.title.trim() : "";
	const description = typeof payload.description === "string" ? payload.description.trim() : "";
	if (!title || title.length > 500 || description.length > 2_000) {
		return apiErrorResponse("invalid_request", 400, { scope: "meal_input" });
	}

	if (payload.request_kind === "automatic_retry") {
		const category = typeof payload.meal_category === "string" ? payload.meal_category.trim().toLowerCase() : "";
		if (!/^[a-z][a-z0-9 _-]{0,31}$/.test(category)) {
			return apiErrorResponse("invalid_retry_category", 400, { scope: "request" });
		}
		try {
			const reservation = await budgetRequest(env, {
				action: "reserve_retry",
				uid: await sha256Hex(uid),
				category,
				day: _getUtcDate(),
				limit: 8,
			});
			if (!reservation.allowed) {
				return apiErrorResponse("ai_retry_budget_exhausted", 429, {
					scope: "user_budget",
					retryable: true,
					message: `The automatic estimate retry allowance for ${category} has been reached for today. The meal remains queued.`,
					retryAt: _getNextUtcMidnight(),
				});
			}
		} catch (error) {
			console.error("Could not reserve the automatic retry budget", error);
			return apiErrorResponse("ai_budget_unavailable", 503, { scope: "project", retryable: true, retryAt: Date.now() + 30 * 60_000 });
		}
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

	const upstreamResult = await _callGemini(prompt, env, mealSchema);

	if (!upstreamResult.ok) {
		if (upstreamResult.rawText === "ai_budget_unavailable") {
			return apiErrorResponse("ai_budget_unavailable", 503, {
				scope: "project", retryable: true, retryAt: upstreamResult.retryAt ?? Date.now() + 30 * 60_000,
			});
		}
		if (upstreamResult.budgetLimited) {
			return apiErrorResponse("ai_provider_daily_budget_reached", 429, {
				scope: "project",
				retryable: true,
				message: "The nutrition service has reached its daily provider request limit. The meal remains queued.",
				retryAt: upstreamResult.retryAt,
			});
		}
		const error = providerErrorDetails(upstreamResult.status, upstreamResult.rawText);
		return apiErrorResponse(
			error.code,
			upstreamResult.status === 429 ? 429 : 502,
			{ ...error, upstreamStatus: upstreamResult.status },
		);
	}

	let providerResponse: GeminiResponse;
	try {
		providerResponse = JSON.parse(upstreamResult.rawText) as GeminiResponse;
	} catch {
		return apiErrorResponse("ai_invalid_response", 502, { scope: "provider", upstreamStatus: 200, retryable: true });
	}

	if (isContentBlocked(providerResponse)) {
		return apiErrorResponse("ai_content_blocked", 422, { scope: "meal_input", upstreamStatus: 200 });
	}

	const text = extractGeminiText(providerResponse);
	if (!text) {
		return apiErrorResponse("ai_empty_response", 502, { scope: "provider", upstreamStatus: 200, retryable: true });
	}

	try {
		const jsonMatch = text.match(/\{[\s\S]*\}/);
		if (!jsonMatch) {
			return apiErrorResponse("ai_invalid_response", 502, { scope: "provider", upstreamStatus: 200, retryable: true });
		}
		const result = JSON.parse(jsonMatch[0]);
		const rawCalories = extractOptionalNumber(result.total_calories ?? result.calories ?? result.totalCalories);
		const calories = rawCalories !== null && rawCalories >= 0 && rawCalories <= 20_000
			? Math.round(rawCalories)
			: null;

		const normalizeMacro = (value: unknown): number | null => {
			const parsed = extractOptionalNumber(value);
			return parsed !== null && parsed >= 0 ? Math.round(parsed * 10.0) / 10.0 : null;
		};
		const carbs = normalizeMacro(result.carbs_g ?? result.carbs ?? result.carbohydrates ?? result.carbsG);
		const protein = normalizeMacro(result.protein_g ?? result.protein ?? result.proteinG);
		const fat = normalizeMacro(result.fat_g ?? result.fat ?? result.fatG);
		const fiber = normalizeMacro(result.fiber_g ?? result.fiber ?? result.fiberG);

		return Response.json({
			total_calories: calories,
			carbs_g: carbs,
			protein_g: protein,
			fat_g: fat,
			fiber_g: fiber,
			fat_breakdown: typeof result.fat_breakdown === "object" && result.fat_breakdown !== null ? result.fat_breakdown : {},
			carb_breakdown: typeof result.carb_breakdown === "object" && result.carb_breakdown !== null ? result.carb_breakdown : {},
			vitamins: typeof result.vitamins === "object" && result.vitamins !== null ? result.vitamins : {},
			minerals: typeof result.minerals === "object" && result.minerals !== null ? result.minerals : {},
			amino_acids: typeof result.amino_acids === "object" && result.amino_acids !== null ? result.amino_acids : {},
			antioxidants: typeof result.antioxidants === "object" && result.antioxidants !== null ? result.antioxidants : {},
			other_nutrients: typeof result.other_nutrients === "object" && result.other_nutrients !== null ? result.other_nutrients : {},
		});
	} catch {
		return apiErrorResponse("ai_invalid_response", 502, { scope: "provider", upstreamStatus: 200, retryable: true });
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
		"- A zero value is valid when a nutrient is absent or negligible. Do not invent a positive macro value just to make all macros nonzero or to match calories.",
		"- Return numeric values for calories, carbs_g, protein_g, fat_g, and fiber_g, including zero when appropriate. Estimate from the stated ingredients and portion; do not treat an absent ingredient as present.",
		"- Do not force calories to equal a formula based only on carbs, protein, and fat when another energy source or rounding could explain a difference; estimate calories and each nutrient independently.",
		"- Populate vitamin, mineral, amino-acid, antioxidant, and nutrient-breakdown maps only with values reasonably supported by the ingredients and portion. If no values can be estimated reliably for a map, return an empty object; do not invent values to fill it.",
		"- Estimate total calories (kcal), macros in grams (carbs_g, protein_g, fat_g, fiber_g), key vitamins (Vitamin A, Vitamin C, Vitamin D, Vitamin B12, Vitamin B6, Folate, Choline), key minerals (Calcium, Iron, Potassium, Sodium, Zinc, Magnesium, Selenium, Phosphorus), essential amino acids (Leucine, Lysine, Valine, Tryptophan), antioxidants (Polyphenols, Lycopene, Lutein), and other nutrients (Saturated Fat, Total Sugars, Cholesterol, Omega-3, Food Water Content).",
		"- Output total_calories, carbs_g, protein_g, fat_g, and fiber_g strictly as raw numeric numbers (e.g. 450, 45.0, 25.0), NOT string values with units.",
		"- Populate any returned nutrient maps with specific nutrient names as keys and values with appropriate units (for example, \"Vitamin C\": \"value mg\"). Do NOT use literal key names like \"name\".",
		"- Keep response concise and output strictly valid JSON matching the requested schema. Any example values are format hints only; calculate all values for this meal.",
	].join("\n");
}

async function handleWorkoutMet(request: Request, env: Env, uid: string): Promise<Response> {
	if (request.method !== "POST") {
		return apiErrorResponse("method_not_allowed", 405, { scope: "request" });
	}

	const apiKeys = _getGeminiApiKeys(env as unknown as Record<string, string | undefined>);
	if (apiKeys.length === 0) {
		return apiErrorResponse("ai_provider_not_configured", 503, { scope: "project" });
	}

	let payload: Record<string, unknown>;
	try {
		payload = await request.json() as Record<string, unknown>;
	} catch {
		return apiErrorResponse("invalid_json", 400, { scope: "request" });
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
		return apiErrorResponse("invalid_request", 400, { scope: "request" });
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
	const upstreamResult = await _callGemini(prompt, env);

	if (!upstreamResult.ok) {
		if (upstreamResult.rawText === "ai_budget_unavailable") {
			return apiErrorResponse("ai_budget_unavailable", 503, {
				scope: "project", retryable: true, retryAt: upstreamResult.retryAt ?? Date.now() + 30 * 60_000,
			});
		}
		if (upstreamResult.budgetLimited) {
			return apiErrorResponse("ai_provider_daily_budget_reached", 429, {
				scope: "project", retryable: true, retryAt: upstreamResult.retryAt,
			});
		}
		const error = providerErrorDetails(upstreamResult.status, upstreamResult.rawText);
		return apiErrorResponse(
			error.code,
			upstreamResult.status === 429 ? 429 : 502,
			{ ...error, upstreamStatus: upstreamResult.status },
		);
	}

	let providerResponse: GeminiResponse;
	try {
		providerResponse = JSON.parse(upstreamResult.rawText) as GeminiResponse;
	} catch {
		return apiErrorResponse("ai_invalid_response", 502, { scope: "provider", upstreamStatus: 200, retryable: true });
	}

	const text = extractGeminiText(providerResponse);
	if (!text) {
		return apiErrorResponse("ai_empty_response", 502, { scope: "provider", upstreamStatus: 200, retryable: true });
	}

	try {
		const result = JSON.parse(text.replace(/^```json\s*/i, "").replace(/\s*```$/, ""));
		const met = Number(result.base_met);
		if (!Number.isFinite(met) || met < 1 || met > 15) {
			return apiErrorResponse("ai_invalid_response", 502, { scope: "provider", upstreamStatus: 200, retryable: true });
		}
		return Response.json({ base_met: met });
	} catch {
		return apiErrorResponse("ai_invalid_response", 502, { scope: "provider", upstreamStatus: 200, retryable: true });
	}
}

function providerErrorDetails(
	status: number,
	rawText = "",
): { code: string; scope: ApiErrorScope; retryable: boolean } {
	if (isLocationRejected({ status, rawText })) {
		return { code: "ai_provider_location", scope: "location", retryable: false };
	}
	if (isQuotaExhausted({ status, rawText })) {
		return { code: "ai_provider_quota", scope: "key_model", retryable: true };
	}
	if (status === 413) {
		return { code: "ai_payload_too_large", scope: "meal_input", retryable: false };
	}
	if (status === 401 || status === 403) {
		const projectWide = /billing|verification|project|free tier|organization|payment|suspended|restricted/i.test(rawText);
		return { code: "ai_provider_auth_error", scope: projectWide ? "project" : "key", retryable: false };
	}
	if (status === 404 || (status === 400 && /model/i.test(rawText))) {
		return { code: "ai_model_unavailable", scope: "model", retryable: false };
	}
	if (status === 400) {
		return { code: "ai_provider_bad_request", scope: "meal_input", retryable: false };
	}
	if (status === 429) {
		// The Worker already walks configured keys/models in this request. The app
		// may make a later scheduled retry after the provider cooldown has elapsed.
		return { code: "ai_provider_rate_limited", scope: "key_model", retryable: true };
	}
	if (status === 0 || status === 408 || (status >= 500 && status !== 501)) {
		return {
			code: "ai_provider_busy",
			scope: "provider",
			retryable: true,
		};
	}
	if (status === 501) {
		return { code: "ai_model_unavailable", scope: "model", retryable: false };
	}
	return { code: "ai_provider_error", scope: "provider", retryable: false };
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
	budgetLimited?: boolean;
	retryAt?: number;
};

export async function _callGemini(prompt: string, env: Env, responseSchema?: Record<string, unknown>): Promise<GeminiCallResult> {
	const apiKeys = _getGeminiApiKeys(env as unknown as Record<string, string | undefined>);
	if (apiKeys.length === 0) {
		return { ok: false, status: 503, rawText: "ai_provider_not_configured", attempts: [] };
	}

	const headers = {
		accept: "application/json",
		"content-type": "application/json",
	};

	const genConfig: Record<string, unknown> = {
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
	// A throttled key is skipped for this model, but remains eligible for the next model.
	const skippedKeyModelsForRequest = new Set<string>();
	const quotaDate = getProviderQuotaDate();
	// Model-first approach: try each model across all keys before moving to its fallback.
	const kind = responseSchema ? "meal" : "workout";
	for (const model of GEMINI_MODELS) {
		for (const keySlot of apiKeys) {
			const requestPairId = keyModelId(keySlot.key, model);
			let persistentlyExhausted = false;
			try {
				const pairId = await sha256Hex(requestPairId);
				persistentlyExhausted = !(await budgetRequest(env, { action: "is_pair_exhausted", day: quotaDate, pairId })).allowed;
			} catch (error) {
				console.error("Could not check persisted Gemini key/model quota state", error);
				return { ok: false, status: 503, rawText: "ai_budget_unavailable", attempts };
			}
			if (skippedKeyModelsForRequest.has(requestPairId) || persistentlyExhausted) {
				console.info(`Skipping exhausted key/model pair: ${keySlot.label} on ${model}`);
				continue;
			}
			let shouldTryCompatibility = false;
			let skipRemainingAttemptsForKey = false;

			for (const [payloadIndex, payload] of [structuredPayload, compatibilityPayload].entries()) {
				if (payloadIndex === 1 && !shouldTryCompatibility) break;
				if (skipRemainingAttemptsForKey) break;

				for (let attemptNumber = 0; attemptNumber < GEMINI_ATTEMPTS_PER_MODEL; attemptNumber += 1) {
					try {
						const providerLimit = Number.parseInt(env.GEMINI_MAX_UPSTREAM_ATTEMPTS_PER_DAY ?? "", 10);
						const reservation = await budgetRequest(env, {
							action: "reserve_provider_attempt",
							day: quotaDate,
							limit: Number.isInteger(providerLimit) && providerLimit > 0 ? providerLimit : DEFAULT_GEMINI_UPSTREAM_ATTEMPT_LIMIT,
						});
						if (!reservation.allowed) {
							return {
								ok: false, status: 429, rawText: "ai_provider_daily_budget_reached", attempts,
								budgetLimited: true, retryAt: _getNextPacificMidnight(),
							};
						}
					} catch (error) {
						console.error("Could not reserve Gemini upstream attempt budget", error);
						return { ok: false, status: 503, rawText: "ai_budget_unavailable", attempts, retryAt: Date.now() + 30 * 60_000 };
					}
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
						// This is based on request egress location/IP classification, so changing
						// keys/models from the same Worker cannot fix it and only spends quota.
						return { ok: false, status: attempt.status, rawText: attempt.rawText, attempts };
					}

					if (attempt.status === 413) {
						// Retrying the same oversized meal prompt with other keys/models cannot help.
						return { ok: false, status: attempt.status, rawText: attempt.rawText, attempts };
					}

					// The configured policy skips a throttled key/model pair for the
					// provider day. Other models and keys remain eligible.
					if (attempt.status === 429) {
						skippedKeyModelsForRequest.add(requestPairId);
						try {
							await budgetRequest(env, {
								action: "mark_pair_exhausted",
								day: quotaDate,
								pairId: await sha256Hex(requestPairId),
							});
						} catch (error) {
							console.error("Could not persist Gemini key/model throttling", error);
							return { ok: false, status: 503, rawText: "ai_budget_unavailable", attempts };
						}
						console.warn(`Key/model pair throttled for today: ${keySlot.label} on ${model}`);
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
	return status === 0 || status === 408 || status === 429 || (status >= 500 && status !== 501);
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
	promptFeedback?: { blockReason?: string };
	candidates?: Array<{ content?: { parts?: Array<{ text?: string }> }; finishReason?: string }>;
};

function isContentBlocked(response: GeminiResponse): boolean {
	const blockedReasons = new Set(["SAFETY", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "RECITATION"]);
	return blockedReasons.has(response.promptFeedback?.blockReason ?? "") ||
		(response.candidates ?? []).some((candidate) => blockedReasons.has(candidate.finishReason ?? ""));
}

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

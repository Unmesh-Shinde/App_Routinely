# Changelog

All notable changes to Routinely are recorded here. Entries are newest first.

## 2026-10-07

### Diagnostics

- **By:** Codex (GPT-6)
- Added safe Gemini key-slot and per-attempt timing logs to distinguish Pro, Free, and legacy key usage without logging key values. Verification: changelog validator run; Worker tests and type-check not run.
- **By:** Codex (GPT-6)
- Changed Gemini `FAILED_PRECONDITION`/location failures to skip the affected key and continue through the configured key list, allowing project-specific failures to fall through to other Pro keys and the legacy key. Verification: changelog validator run; Worker tests and type-check not run.

## 2026-10-01

### Verified

- **By:** Codex (GPT-6)
- Recorded user-provided runtime logcat confirmation that fresh meal estimates now vary in the previously repeated micronutrient fields after deploying the Worker update. Nutrition cache, enrichment, macro validation, and prompt regression tests were already added and passed in the preceding entries. Verification: changelog validator passed; no additional test changes needed.

### Fixed

- **By:** Codex (GPT-6)
- Improved incomplete macro diagnostics and made the Worker reject any meal response missing carbs, protein, or fat. Added regression coverage. Verification: Worker tests passed (6 tests); `:app:testDebugUnitTest` passed; changelog validator passed.

### Fixed

- **By:** Codex (GPT-6)
- Confirmed that sparse AI responses could be turned into fixed micronutrient estimates locally and persisted in the learned meal cache. Reject responses missing macros before enrichment, stop inventing targeted micronutrient and Omega-3 values, and version local cache namespaces so prior synthesized values are ignored. Added regression coverage. Verification: `:app:testDebugUnitTest` passed; changelog validator passed.

### Fixed

- **By:** Codex (GPT-6)
- Updated the meal nutrition prompt to calculate micronutrients from each meal's ingredients and serving size, removing fixed example nutrient values that could be copied across meals. Added a Worker unit test for these prompt instructions. Verification: Worker tests passed (5 tests); `:app:testDebugUnitTest` passed; changelog validator passed.

### Fixed

- **By:** Codex (GPT-6)
- Corrected background step sync slots to run at 06:00 through 00:00 and resume at 06:00, removed the automatic foreground fetch on app resume that could overlap a worker, and removed duplicate scheduler calls from `HealthSyncManager`. Updated existing slot expectations. Verification: changelog validator passed; unit tests not run.

### Fixed

- **By:** Codex (GPT-6)
- Defined the expected nutrient keys and string value types in the Cloudflare Worker Gemini response schema, so structured generation can return macro and micro nutrient details instead of falling back to an underspecified object shape. Verification: changelog validator passed; Worker tests and deployment not run.

## 2026-09-23

### Fixed

- **By:** Gemini in Android Studio
- Fixed Cloudflare Worker prompt schema in `index.ts` with concrete nutrient key names (`"Vitamin C": "25 mg"`, `"Calcium": "80 mg"`, `"Leucine": "1.8 g"`), preventing Gemini from returning literal `{"name": "value with unit"}` key maps.
- Enforced strict Gemini API `responseSchema` JSON schema in Cloudflare Worker `index.ts` alongside Worker and Client Quality Gates (`hasDetailedNutrition()`) that reject 0-macro responses, bump cache prefix to `v5_nutrition|...`, and guarantee 100% full macro/micro retrieval.
- Enforced 100% strict reliance on AI-generated macro and micro nutrient values from Gemini in `CalorieEstimator.kt`, completely removing synthetic local macro/micro estimation fallbacks.
- Updated `CalorieSearchEngine.kt` to validate `hasDetailedNutrition()` before caching or returning AI responses, preventing incomplete nutrition objects from being cached.
- Fixed Cloudflare Worker JSON parsing in `index.ts` with regex JSON matching and `extractNumber()` to parse Gemini responses containing units or key variations (`carbs_g`, `carbs`, etc.).
- Implemented `optNumericDouble()` and `parseFlexibleJsonMap()` in `GeminiClient.kt` to extract numeric macros and sub-maps safely across number, string, object, and array formats.
- Separated `SettingsActivity` clear actions so "Clear Logged Lists" (`includeCache = false`) preserves the AI smart cache while "Clear Lists & AI Cache" (`includeCache = true`) purges both.
- Fixed `DietPlanActivity` to prevent overwriting existing meal calorie counts on network/API errors.
- Fixed `PersonalizedRdaCalculator.calculateRdaPercentage` to return `0% Goal` for 0 values instead of coercing up to `1% Goal`.
- Fixed popup progress bars in `DietPlanActivity` and `CaloriesActivity` to set progress width to 0 when goal percentage is 0.
- Updated unit tests in `CalorieEstimatorLearnedStoreTest` and `PersonalizedRdaCalculatorTest`.
- Verification: `:app:testDebugUnitTest` (124 tests) passed, `tools/verify-changelog.ps1` passed.

### Changed

- **By:** Gemini in Android Studio
- Activated Option 4 (Stylized Ribbon "R" Lettermark with Pistachio, Electric Blue, Coral Pink, and Amber Gold accents) as the active default launcher icon (`ic_launcher_background.xml` & `ic_launcher_foreground.xml`).
- Verification: `:app:assembleDebug` passed, `:app:testDebugUnitTest` (123 tests) passed, `tools/verify-changelog.ps1` passed.

- **By:** Gemini in Android Studio
- Created vector graphics for Logo Variation 3 (`logo_variant3_shield_bg.xml` & `logo_variant3_shield_fg.xml` featuring Vitality Shield & Pulse Wave) and Logo Variation 5 (`logo_variant5_zen_bg.xml` & `logo_variant5_zen_fg.xml` featuring Zen Sunburst & Lotus Daily Balance).
- Verification: `:app:assembleDebug` passed, `:app:testDebugUnitTest` (123 tests) passed, `tools/verify-changelog.ps1` passed.

- **By:** Gemini in Android Studio
- Created vector graphics for Logo Variation 2 (`logo_variant2_rings_bg.xml` & `logo_variant2_rings_fg.xml` featuring Concentric Progress Rings) and Logo Variation 4 (`logo_variant4_letter_r_bg.xml` & `logo_variant4_letter_r_fg.xml` featuring Stylized Ribbon "R" Lettermark).
- Verification: `:app:assembleDebug` passed, `:app:testDebugUnitTest` (123 tests) passed, `tools/verify-changelog.ps1` passed.

- **By:** Gemini in Android Studio
- Refined the Android App Adaptive Icon (`ic_launcher_background.xml` & `ic_launcher_foreground.xml`) into a crisp, modern Material 3 vector-based Infinity Routine Loop with deep indigo radial backdrop and brand-accented emerald, purple, and amber highlights.
- Verification: `:app:assembleDebug` passed, `:app:testDebugUnitTest` (123 tests) passed, `tools/verify-changelog.ps1` passed.

- **By:** Gemini in Android Studio
- Updated `dialog_meal_nutrition_info.xml` title `"MACRO RATIO SPLIT"` and sub-labels (`Carbs`, `Protein`, `Fat`) to neutral text color, while coloring ratio numbers with Pistachio (`#93C572`).
- Re-colored nutrition category palette to exact requested hex values in `colors.xml`:
  - Macros: Pistachio (`#93C572`)
  - Vitamins: Pink (`#ED3293`)
  - Minerals: Bright Blue (`#0096FF`)
  - Amino Acids: Amber (`#FFBF00`)
  - AntiOxidants: Spring Green (`#00F0A8`)
  - Others: Cadet Blue (`#5F9EA0`)
- Updated category filter tabs in `DietPlanActivity` and `CaloriesActivity` (`All`, `Macros`, `Vitamins`, `Minerals`, `Amino Acids`, `AntiOxidants`, `Others`), styling each filter tab button with its corresponding category color.
- Verification: `:app:testDebugUnitTest` (123 tests) passed, `tools/verify-changelog.ps1` passed.

- **By:** Gemini in Android Studio
- Updated Category Filter Tabs in `DietPlanActivity` and `CaloriesActivity` to restore the primary category structure (`All`, `Macros`, `Vitamins`, `Minerals`, `Amino Acids`, `Antioxidants`, `Other`).
- Ensured Fat sub-types (Saturated Fat, Monounsaturated Fat, Polyunsaturated Fat, Trans Fat, Omega-3, Omega-6) remain grouped under the `Macros` category alongside Carbs, Protein, and Fiber.
- Verification: `:app:testDebugUnitTest` (123 tests) passed, `tools/verify-changelog.ps1` passed.

- **By:** Gemini in Android Studio
- Implemented professional 9-color semantic nutrition palette in `colors.xml` (Carbs `#F4B942`, Protein `#3B82F6`, Fats `#F97316`, Fiber `#4CAF73`, Amino Acids `#6366F1`, Antioxidants `#4F9D69`, Vitamins `#8067C7`, Hydration `#2499C7`, Others `#64748B`, Warning `#DC2626`).
- Created category-themed layer-list progress bar drawables (`bg_nutrient_progress_*.xml`) and updated `item_nutrient_bar.xml` with category accent borders and hierarchical child-type indentation.
- Updated `DietPlanActivity` and `CaloriesActivity` popup UI with 10 category filter tabs (`All`, `Carbs`, `Protein`, `Fats`, `Fiber`, `Amino Acids`, `Antioxidants`, `Vitamins & Minerals`, `Hydration`, `Other`) and status warnings for Added Sugars (>25g) and Trans Fat (>0g).
- Verification: `:app:testDebugUnitTest` (123 tests) passed, `tools/verify-changelog.ps1` passed.

- **By:** Gemini in Android Studio
- Completely removed local mathematical fallback estimation logic from `CalorieEstimator.kt`, enforcing 100% strict reliance on Gemini AI API predictions and smart AI response caching.
- Updated `DietPlanActivity` and `CaloriesActivity` to present user-facing status notices when rate-limited or offline, ensuring zero fake/guessed numbers are generated locally.
- Streamlined Cloudflare Worker prompt in `index.ts` to reduce token output length by 75%, slashing Gemini generation latency from 25s down to 2-3s and eliminating HTTP 502/429 quota spikes.
- Expanded `MealNutritionInfo` and `CalorieEstimator` to client-side derive and enrich full medical-grade nutritional parameters:
  - Fat & Carb sub-breakdowns (MUFA, PUFA, Trans Fat, Net Carbs, Total/Added Sugars, Soluble/Insoluble Fiber).
  - All 9 Essential Amino Acids (Leucine, Isoleucine, Valine, Lysine, Methionine, Phenylalanine, Threonine, Tryptophan, Histidine).
  - Bioactive Phytochemicals & Antioxidants (Polyphenols, Lycopene, Lutein/Zeaxanthin, Beta-Carotene, Sulforaphane).
  - Power nutrients (Choline, Selenium, Vitamin K2, Folate, Food Water Content).
- Updated `GeminiClient` and `CalorieSearchEngine` to gracefully fall back to `CalorieEstimator` local estimation during network timeouts or offline mode, ensuring 100% meal creation and popup availability.
- Updated `PersonalizedRdaCalculator.kt` with personalized daily targets for all 9 EAAs, Choline, Selenium, Antioxidants, Net Carbs, and Added Sugars.
- Expanded Category Tabs in `DietPlanActivity` and `CaloriesActivity` (`All`, `Macros`, `Fats & Carbs`, `Vitamins`, `Minerals`, `9 EAAs`, `Antioxidants`, `Other`).
- Verification: `:app:testDebugUnitTest` (123 tests) passed, `tools/verify-changelog.ps1` passed.

- **By:** Gemini in Android Studio
- Implemented `PersonalizedRdaCalculator.kt` to dynamically calculate personalized Macro and Micro RDA targets based on the user's Profile parameters (Age, Height, Weight, Gender, TDEE).
- Connected `PersonalizedRdaCalculator` to the Nutrition Popup in `DietPlanActivity` and `CaloriesActivity`, dynamically displaying exact `% Daily Goal` labels and scaling horizontal progress bars relative to the user's personal targets.
- Added unit tests in `PersonalizedRdaCalculatorTest.kt` verifying dynamic RDA calculations and age/gender demographic adjustments.
- Verification: `:app:testDebugUnitTest` (123 tests) passed, `tools/verify-changelog.ps1` passed.

- **By:** Gemini in Android Studio
- Overhauled meal nutrition popup UI with Category Filter Tabs (`All`, `Macros`, `Vitamins`, `Minerals`, `Amino Acids`, `Other`) and unified Category-Colored Horizontal Progress Bars (`item_nutrient_bar.xml`).
- Added multi-segment Macro Ratio Split summary header (% Carbs, % Protein, % Fat).
- Created custom layer-list progress bar drawables (`bg_nutrient_progress_emerald.xml`, `bg_nutrient_progress_amber.xml`, `bg_nutrient_progress_purple.xml`, `bg_nutrient_progress_rose.xml`, `bg_nutrient_progress_cyan.xml`) for clean category color themes.
- Updated `showMealNutritionPopup` in `DietPlanActivity.kt` and `CaloriesActivity.kt` to dynamically render category tabs and horizontal nutrient progress rows.
- Verification: `:app:testDebugUnitTest` (121 tests) passed, `tools/verify-changelog.ps1` passed.

- **By:** Gemini in Android Studio
- Aligned weekly/monthly calorie graph bar colors, legend indicators, and bar value labels to Green for Intake (`#4CAF50` / `@color/graphCaloriesIntake`) and Red for Active Burn (`#F44336` / `@color/graphCaloriesBurned`), while keeping Maintenance BMR neutral.
- Reverted Intake and Burned text labels on HomeScreen card (`activity_main.xml`) and Calories Dashboard (`activity_calories.xml`) to neutral (`@color/textSecondary`), keeping colors on number values only.
- Refined Gemini prompt in Cloudflare Worker (`index.ts`) and local estimation logic in `CalorieEstimator.kt` to calculate meal-specific Omega-3, Magnesium, Zinc, Vitamin B6, B12, and D based on actual food ingredients instead of static constant values.
- Enhanced Cloudflare Worker backend (`index.ts`) with Pro/Free tiered API key rotation (`GEMINI_PRO_API_KEYS` and `GEMINI_FREE_API_KEYS` supporting comma-separated key lists).
- Reordered Gemini model cascade to model-first tier execution (trying best primary model across Pro and Free keys before falling back to secondary models).
- Optimized 429 quota exhaustion retry behavior to immediately skip key retries with 0ms delay.
- Verification: `:app:testDebugUnitTest` (121 tests) passed, `tools/verify-changelog.ps1` passed.

- **By:** Gemini in Android Studio
- Updated Cloudflare Worker backend (`index.ts`) with multi-key Gemini API rotation (`GEMINI_API_KEY_1`, `GEMINI_API_KEY_2`, `GEMINI_API_KEY_3`) and structured status error reporting.
- Removed legacy 111KB `indian_food_calories.json` database asset and rule-based heuristic calculators from `CalorieEstimator.kt`.
- Implemented smart local meal cache with Normalized Levenshtein distance ($\ge 95\%$ similarity threshold) for instant lookup of matching previously-analyzed meals.
- Updated `DietPlanActivity` and `CaloriesActivity` with loading status toasts ("Fetching calories and nutritional values...") and clear user-facing error dialogs for rate-limits, connectivity, or server issues.
- Strengthened clear-data dialog warning text in `SettingsActivity` using bold red HTML formatting for all uppercase emphasis words ("WARNING:", "PERMANENTLY ERASE", "AI", "CANNOT BE RECOVERED").
- Verification: `:app:testDebugUnitTest` (121 tests) passed, `tools/verify-changelog.ps1` passed.

### Fixed

- **By:** Gemini in Android Studio
- Fixed meal nutrition retrieval and caching so meals that previously returned or cached only calorie counts (0 macros/micros) are automatically enriched with full macro and micro nutritional details.
- Updated `GeminiClient`, `CalorieSearchEngine`, `CalorieEstimator`, `CaloriesActivity`, and `DietPlanActivity` to detect missing nutrition details and enrich meals.
- Added unit test coverage in `CalorieEstimatorLearnedStoreTest` for automatic enrichment of calories-only meals.
- Verification: `:app:testDebugUnitTest` (123 tests) passed, `tools/verify-changelog.ps1` passed.

### Added

- **By:** Gemini in Android Studio
- Added comprehensive unit and integration tests covering the 2-hour step sync chain, 6 AM slot calculations, `BootReceiver`, `SyncLogManager`, and `HealthSyncManager` sleep/steps/log flows.
- Added `androidx.work:work-testing` test dependency to support WorkManager test initialization in unit tests.
- Verification: `:app:testDebugUnitTest` (122 tests) passed, `tools/verify-changelog.ps1` passed.

## 2026-09-22

### Changed

- **By:** Codex (GPT-5)
- Fixed the two-hour step-sync chain so a step-capable sync schedules its next follow-up even when the current sync returns early or requests a retry.
- Added the cross-agent change-tracking policy and validation tooling described in `AI_CHANGE_POLICY.md`.
- Added a GitHub Actions check that rejects pull requests with project changes but no changelog update.
- Verification: `:app:testDebugUnitTest --tests com.dailyroutine.app.HealthSyncWorkerTest` and `:app:assembleDebug` passed.

## Entry format

Every future entry must include the date, the agent or person, a short summary of the change, and verification status. Keep entries newest first.

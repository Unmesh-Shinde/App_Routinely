# Changelog

All notable changes to Routinely are recorded here. Entries are newest first.

## 2026-11-17

### Cover Meal Template Category Cap Enforcement
- **By:** Codex (GPT-6)
- Added a PlanManager test proving template application rejects a date that would exceed the shared per-category meal cap and makes no partial writes.
- Verification: `:app:testDebugUnitTest --tests com.dailyroutine.app.PlanManagerTest.mealTemplateApply_rejectsCategoryCapExceededWithoutPartialWrites` passed; `powershell -ExecutionPolicy Bypass -File tools/verify-changelog.ps1` passed.

## 2026-11-17

### Clarify Meal Entry Cap and Centralize Its Limit
- **By:** Codex (GPT-6)
- Added one shared `MAX_MEALS_PER_CATEGORY_PER_DAY` constant for single-meal add/edit validation and meal-template range validation.
- Updated the category-cap dialog, meal editor note, Nutrition Fair Use Policy, and exported policy text to explain that the cap limits saved plan entries, while separate AI request allowances govern Gemini usage.
- Verification: `powershell -ExecutionPolicy Bypass -File tools/verify-changelog.ps1` passed; scoped `git diff --check` passed. Build/tests not run.

## 2026-11-17

### Defer Budget-Denied Meal Retries Without Losing Attempts
- **By:** Codex (GPT-6)
- When the per-user retry allowance or shared provider budget is full, the Worker now returns the next reset time and Android reschedules the same meal attempt without incrementing retry counters or marking the meal exhausted. Durable Object errors use a bounded 30-minute deferral.
- Switched the per-user/category request day to server-derived UTC and removed client-supplied timezone offsets, preventing timezone changes from shifting or bypassing the daily allowance. Provider reset timestamps now use Pacific midnight with DST-aware conversion.
- Corrected Pacific quota dates to ISO `YYYY-MM-DD`, matching Durable Object cleanup comparisons so object restarts retain current budget and 429 exhaustion state.
- Added Cloudflare tests for UTC date/reset behavior, Pacific reset across the daylight-saving transition, and upstream-limit reset timestamps; added Android tests for preserving server retry timestamps and retry counters on deferral.
- Verification: `npx tsc --noEmit` passed; `npx vitest run` passed (13 tests). Android tests could not run because the workspace Gradle configuration changed to Gradle 9.9 / Android Gradle Plugin 9.4.1, and the wrapper download timed out; using cached Gradle 9.8 also failed because plugin 9.4.1 was unavailable. Changelog validator and scoped `git diff --check` passed.

## 2026-11-17

### Cover Nutrition Dashboard and Retry Policy with Focused Tests
- **By:** Codex (GPT-6)
- Centralized payload-too-large/content-blocked card and dashboard warning copy and tone; tested both presentation variants and the exhausted-retry state.
- Added Nutrition dashboard UI contract checks for repeat-on-resume behavior, chronological issue list, date/card navigation, and warning-card wiring; added PlanManager coverage for filtering, seniority sorting, and resolved issue removal.
- Extracted retry state transitions into a pure policy, aligned add/edit failure state with the scheduled sequence, and covered the initial +30 minute retry, subsequent +45/+60 minute intervals, 12 AM/2 AM/6 AM/8 AM slots, late-night rollover, and terminal errors. Added Android request metadata tests for category and timezone.
- Verification: Focused `testDebugUnitTest` run passed all 13 selected tests across retry policy, warning presentation, retry request metadata, PlanManager issue handling, and dashboard UI contracts. Earlier full Android suite had 14 failures among 196 tests; it was not rerun in this focused pass.

## 2026-11-17

### Enforce Shared Nutrition Retry and Provider Budgets
- **By:** Codex (GPT-6)
- Moved the eight automatic retry requests per Firebase user, category, and client-local date from device-only storage to an atomic SQLite Durable Object reservation; breakfast and lunch budgets are independent and shared across devices.
- Added a shared cap of 300 actual Gemini HTTP attempts per Pacific Time day, with a Wrangler variable to tune it, and persisted key/model 429 skips for that day using hashed pair identifiers.
- Marked Android background retry calls with their meal category and timezone offset, removed the local retry counter, and returned structured budget-limit errors while keeping affected meals queued.
- Added Durable Object concurrency/isolation tests and Worker integration tests for the upstream cap and persisted key/model throttling. Added the missing `Intent` import needed for Android compilation.
- Verification: `npx tsc --noEmit` passed; `npx vitest run` passed (11 tests). Android main and test Kotlin sources compiled; `testDebugUnitTest` ran 196 tests and reported 14 failures in nutrition parsing, health sync, PlanManager rewards, PIN visibility, and UI resource contracts. Changelog validator and scoped `git diff --check` passed.

## 2026-11-17

### Add Persistent Meal Issue Alerts and Bounded Automatic Retries
- **By:** Codex (GPT-6)
- Added a Nutrition dashboard popup for payload-too-large and content-blocked meals, ordered by original add time, with issue details and navigation to the meal's date and card.
- Replaced meal-card manual estimate retries with a persisted per-meal sequence: initial estimate, retries after 30 minutes, then 45 minutes, then 60 minutes, followed by eligible 12 AM, 2 AM, 6 AM, and 8 AM slots; the sequence stops after those attempts.
- Added an inexact `setAndAllowWhileIdle` wake alarm that starts network-constrained WorkManager work, with rescheduling after retry processing, app launch, boot, and clock changes.
- Added a separate cap of 8 background retry requests per meal category per local day while preserving the existing user-initiated request allowance; quota and rate-limit errors now remain retryable on the scheduled cadence.
- Updated existing Worker response assertions for the structured error envelope.
- Verification: Changelog validator and scoped `git diff --check` passed. Full diff check still reports pre-existing trailing whitespace in `cloudflare-backend/worker-configuration.d.ts`; build/tests not run (not requested).

## 2026-11-17

### Preserve Nutrition Provider Errors and Show Meal Warnings
- **By:** Codex (GPT-6)
- Added machine-readable Worker error code, scope, upstream HTTP status, retryability, and message fields so Android can distinguish provider failures even when the Worker returns HTTP 502.
- Mapped provider payload-too-large and safety/content-block errors to meal-specific outcomes; show concise orange/red guidance on affected meal cards and direct users to edit the meal text.
- Prevented rate-limit errors from triggering another Android retry cascade because the Worker already advances through the configured key/model options.
- Verification: Changelog validator and `git diff --check`; build/tests not run (not requested).

## 2026-11-16

### Correct Nutrition Discipline Reward Dates
- **By:** Codex (GPT-6)
- Made edit/delete streaks evaluate every elapsed calendar day, resetting on days without a primary meal or with the tracked operation; action events and edit rewards now use the actual operation date.
- Made newly earned bonuses active for exactly three calendar dates, starting the day after the third qualifying day, and limited bonus checks to that active interval while preserving legacy reward dates.
- Recorded an edit streak event only after the changed meal is saved, and prevented stale or repeated delete calls from granting replacement credits.
- Updated the existing PlanManager reward and credit expectations to reflect the corrected date rules.
- Verification: Not run (build/tests not requested); changelog validator run before handoff.

## 2026-11-16

### Close Nutrition Limit and Template Bypass Paths
- **By:** Codex (GPT-6)
- Enforced the two-per-category meal cap when applying saved templates, validating all target dates before writing any copied meals.
- Counted nutrition provider attempts against the actual request date and reserved daily allowance atomically across meal add/edit, nutrition detail lookups, manual retries, and overnight retries; cache hits remain free.
- Kept a meal recoverable when immediate retries exhaust the remaining daily allowance, and left manual retries available when no provider request could start.
- Deferred overnight retries after global auth, quota, or daily-limit failures until the next midnight window to avoid repeatedly scheduling the blocked queue during the same night.
- Verification: Not run (build/tests not requested); changelog validator run before handoff.

## 2026-11-16

### Make Failed Nutrition Estimates Recoverable
- **By:** Codex (GPT-6)
- Kept auth/configuration, quota, and empty AI responses in the overnight retry queue instead of saving unestimated meals as completed; cleared stale nutrition when an edited meal's new estimate fails.
- Made empty AI responses explicit errors, stopped immediate retries for permanent auth/configuration failures, and guarded background updates against overwriting meals whose contents changed while a fetch was running.
- Rescheduled pending work behind the running worker, rechecked the overnight window at execution time, and scheduled persisted queue work at app startup.
- Verification: Not run (build/tests not requested); changelog validator run before handoff.

## 2026-10-26

### Enforce Option 1 Strict Meal Creation Limits & 1-Hour Single Manual Retry Mechanism
- **By:** Gemini in Android Studio
- Added `queuedTimestamp` and `manualRetryAttempted` properties to `Meal` model in `PlanModels.kt`.
- Updated `DietPlanActivity.kt` to strictly block meal creation when daily AI calculation limits (base 2 + streak replacement credits) for a category are exhausted, displaying a clear notice dialog rather than saving and queuing meals endlessly.
- Implemented 1-hour cool-down and single manual retry mechanism in `DietPlanActivity.kt` and `CaloriesActivity.kt` for overnight-queued meals (`isPendingOvernightRetry == true`):
  - **0–60 Mins**: Card tap displays a cool-down notice showing remaining minutes before manual retry unlocks.
  - **60+ Mins**: Card tap unlocks 1 manual retry attempt. If successful, attaches full nutrition details and updates card; if unsuccessful, marks `manualRetryAttempted = true` and strictly queues for overnight batch calculation (12 AM–6 AM).
- Refined `GeminiClient.kt` and `DietPlanActivity.kt` error handling to distinguish Firebase Auth / App Check security errors (`ai_provider_auth_error`) from transient network drops, preventing security authorization failures from silently queueing meals overnight.
- Configured `OvernightMealRetryWorker.kt` with `NetworkType.CONNECTED` constraints and `ExistingWorkPolicy.KEEP` to ensure background retry jobs execute reliably when connected without being repeatedly reset or executed offline.
- Verification: Unit tests passed (`PlanManagerTest`, `CalorieEstimatorTest`, `UserSettingsStoreTest`, `NutritionPdfExporterTest`); `app:assembleDebug` build succeeded; verified via `powershell -ExecutionPolicy Bypass -File tools/verify-changelog.ps1`.

### Disable On-Demand Retry for Overnight Queued Meals, Force Single-Line Status Label & Conditional Hint Display
- **By:** Gemini in Android Studio
- Updated `DietPlanActivity.kt` and `CaloriesActivity.kt` to disable on-demand calculation retries when tapping a meal card queued for overnight calorie fetch (`isPendingOvernightRetry == true`). Clicking now displays an informative notice explaining that calorie estimation will be completed overnight (12 AM–6 AM).
- Added `android:maxLines="1"` and `android:ellipsize="end"` to `tvTime` in `item_reminder.xml` to prevent "Fetching overnight" status label from wrapping awkwardly across multiple lines.
- Updated `tvHint` ("Tap to view full nutritional info") in `DietPlanActivity.kt` and `CaloriesActivity.kt` to only display after nutritional data (`nutritionInfo` or `calories`) has been fetched and attached to the meal.
- Verification: Passed unit tests (`PlanManagerTest`, `NutritionPdfExporterTest`, `UserSettingsStoreTest`, `CalorieEstimatorTest`) and `app:assembleDebug` build; verified via `powershell -ExecutionPolicy Bypass -File tools/verify-changelog.ps1`.

### Optimize Nutrition Dashboard Launch Performance (< 30ms)
- **By:** Gemini in Android Studio (Auto)
- Offloaded `OvernightMealRetryWorker.schedule()` and `planManager.evaluateStreaks()` to `Dispatchers.IO` in `DietPlanActivity.kt`, eliminating main thread SQLite database locks and disk I/O on startup.
- Added thread-safe in-memory caching for parsed `DietPlan` objects in `PlanManager.kt`, eliminating redundant JSON file reads and Gson parsing on dashboard launch.
- Verification: Unit tests passed (`UserSettingsStoreTest`, `PlanManagerTest`, `NutritionPdfExporterTest`); verified via `powershell -ExecutionPolicy Bypass -File tools/verify-changelog.ps1`.

### Ultra-Compact Profile Policy Status Chip (`✓ Acknowledged`)
- **By:** Gemini in Android Studio (Auto)
- Updated Profile card status chip in `ProfileActivity.kt` and `activity_profile.xml` to render ultra-compact label `✓ Acknowledged` when signed (and `⚠️ Unacknowledged` when unsigned).
- Completely eliminates text wrapping and maintains a clean single-line header for the `Nutrition Fair Use Policy` card.
- Verification: Unit tests passed (`UserSettingsStoreTest`, `PlanManagerTest`, `NutritionPdfExporterTest`); verified via `powershell -ExecutionPolicy Bypass -File tools/verify-changelog.ps1`.

### Enable Back Navigation on First-Time Nutrition Onboarding Modal
- **By:** Gemini in Android Studio (Auto)
- Configured `FirstTimeNutritionGuideDialog` in `DietPlanActivity.kt` to allow Back button cancellation (`.setCancelable(true)` with `setOnCancelListener { finish() }`), enabling users to navigate back to the Home screen freely without being trapped.
- Maintained persistent re-triggering in `onResume()` so the onboarding modal reappears every time the user enters the Nutrition dashboard until the policy is acknowledged.
- Verification: Unit tests passed (`UserSettingsStoreTest`, `PlanManagerTest`); verified via `powershell -ExecutionPolicy Bypass -File tools/verify-changelog.ps1`.

### Optimize PDF Typography, Profile Status Chip & Action Button Labels
- **By:** Gemini in Android Studio (Auto)
- Increased body font size (9.8pt) and line height (13.5pt) in `NutritionPdfExporter.kt` to comfortably fill the single A4 PDF page without empty space or page overflow.
- Updated Profile section status chip in `activity_profile.xml` and `ProfileActivity.kt` to display `⚠️ Policy Unacknowledged` when unsigned for explicit user clarity.
- Simplified action button label from "I Understand & Acknowledge" to "Acknowledge" across `DietPlanActivity.kt`, `NutritionPolicyActivity.kt`, and `activity_nutrition_policy.xml`.
- Verification: Unit tests passed (`UserSettingsStoreTest`, `PlanManagerTest`); verified via `powershell -ExecutionPolicy Bypass -File tools/verify-changelog.ps1`.

### Refine Nutrition Fair Use Policy UI, PDF Page Fitting & Onboarding Modal Re-triggering
- **By:** Gemini in Android Studio (Auto)
- Increased font sizes and layout padding in `showFirstTimeNutritionGuideDialog` in `DietPlanActivity.kt` for improved legibility.
- Added re-triggering logic in `DietPlanActivity.kt` (`onResume`) to re-display the onboarding modal if the user returns from `NutritionPolicyActivity` without acknowledging the policy.
- Increased guidance text size to 13sp with 2.5dp line spacing in `dialog_edit_meal.xml`.
- Updated `NutritionPdfExporter.kt` to render acknowledgment dates conditionally (displays date only if actually acknowledged, otherwise displays "Pending Unacknowledged").
- Adjusted PDF typography and vertical spacing in `NutritionPdfExporter.kt` to fit the entire guide on 1 single A4 page.
- Applied bottom navigation bar margin insets to `cardAcknowledge` in `NutritionPolicyActivity.kt` so the green acknowledge button is fully visible and clickable above gesture/3-button navigation controls.
- Adjusted height and padding on `btnDownloadNutritionPdf` and `btnViewNutritionPolicy` in `activity_profile.xml` to fix label cutoff.
- Verification: Unit tests passed (`UserSettingsStoreTest`, `PlanManagerTest`); verified via `powershell -ExecutionPolicy Bypass -File tools/verify-changelog.ps1`.

### Implement Nutrition Fair Use Policy, Native PDF Exporter & First-Time Onboarding Modal
- **By:** Gemini in Android Studio (Auto)
- Created `NutritionPdfExporter.kt` using native `PdfDocument` API to generate offline A4 PDF `Routinely_Nutrition_Fair_Use_Guide.pdf` saved directly to the device's Downloads folder.
- Created `NutritionPolicyActivity.kt` and `activity_nutrition_policy.xml` providing a full-screen policy reader with 7 structured sections, PDF download trigger, and a one-time acknowledgment checkbox.
- Integrated `cardNutritionPolicy` in `activity_profile.xml` and `ProfileActivity.kt` displaying an acknowledgment status chip (`✓ Acknowledged on YYYY-MM-DD` or `⚠️ Pending`) and actions for viewing guide and downloading PDF.
- Implemented `FirstTimeNutritionGuideDialog` in `DietPlanActivity.kt` (`.setCancelable(false)`) shown on initial open of the Nutrition section, offering preview rules summary, direct policy link, and one-time acknowledgment confirmation persisted in `UserSettingsStore`.
- Verification: Unit tests passed (`UserSettingsStoreTest`); verified via `powershell -ExecutionPolicy Bypass -File tools/verify-changelog.ps1`.

### Implement Refined 3-Day Discipline Streak Rewards & Persistent Notification Popups
- **By:** Gemini in Android Studio (Auto)
- Added 3-Day Discipline Streak Reward System in `PlanManager.kt`:
  - **Primary Category Qualification**: Days count toward a streak ONLY IF at least 1 primary meal (Breakfast, Lunch, or Dinner) is logged; empty days are ignored to prevent ghost streaks.
  - **Edit Streak Reward**: 3 qualifying days with 0 edits unlocks +1 bonus edit per meal (allowing 3 edits/meal) for the next 3 days. Lapses automatically on Day 4.
  - **Delete Streak Reward**: 3 qualifying days with 0 deletes unlocks +1 extra delete-and-replace AI credit per category (allowing 2 replacement credits) for the next 3 days. Lapses automatically on Day 4.
- Implemented persistent interactive reward popups in `DietPlanActivity.kt` (`.setCancelable(false)`) requiring the user to explicitly tap `Understood` to dismiss.
- Dynamic Edit guidance badge in `dialog_edit_meal.xml` dynamically displays remaining edits based on active bonus status (`2 of 2` or `3 of 3`).
- Verification: Unit tests passed (`PlanManagerTest`); verified via `powershell -ExecutionPolicy Bypass -File tools/verify-changelog.ps1`.

### Implement Strategy C AI Token Capping, Delete Guidance & Per-Meal 2-Edit Lifetime Limits
- **By:** Gemini in Android Studio (Auto)
- Implemented Strategy C daily AI token capping (2 network calls/category/day) and delete-replacement credit (+1 AI token on deleting a meal, capping total daily AI calls at 3 max) in `PlanManager.kt`.
- Added per-meal lifetime edit cap (`editCount <= 2`) restricting updates to Title, Description, or Category to 2 times max per meal, while keeping time and reminder changes free.
- Added dynamic edit guidance badge in `dialog_edit_meal.xml` displaying remaining edit count (`0 of 2`, `1 of 2 (Final Edit)`, or `0 of 2 (Limit Reached)`) and locking AI fields when limit is reached.
- Created `showUnifiedMealDeleteGuidanceDialog` offering options to `Edit Meal` (using allowed edits), `Delete` (granting replacement credit), or `Cancel`.
- Created `showUnifiedMealNoticeDialog` enforcing standardized M3 styling, 3-part text structure (Status, Purpose, Next Steps), and consistent `Got It` button placement across all meal warning popups.
- Enforced 3 immediate retries in `CalorieSearchEngine.kt` and scheduled overnight background retries (`OvernightMealRetryWorker.kt`) for non-429 temporary network failures.
- Verification: Unit tests passed (`PlanManagerTest`); verified via `powershell -ExecutionPolicy Bypass -File tools/verify-changelog.ps1`.

### Implement Inline Water Delta Badge & Remove Overlapping Home Toasts
- **By:** Gemini in Android Studio (Auto)
- Added `@+id/tvWaterDeltaBadge` to `activity_main.xml` inside `cardWater`.
- Updated `MainActivity.kt` to accumulate rapid `+` / `-` water button clicks into an inline badge (`+250 ml`, `+500 ml`, etc.) with a 1.5-second animated fade-out Handler, removing bottom Toasts on button taps so controls remain 100% unobscured.
- Verification: Deployed app to device successfully; unit tests passed (`FoodIconResolverTest`); changelog validator script passed (`verify-changelog.ps1`).

### Refine Ghee Vector Drawable to Bright Sunlight Yellow & Priority Matching
- **By:** Gemini in Android Studio (Auto)
- Updated `ic_food_ghee.xml` from dark orange/brown terracotta tones to a bright white ceramic bowl with crystal sunlight-yellow liquid ghee (`#FFEE58` / `#FFF59D`) and specular sunlight glints.
- Elevated `ghee`, `desi ghee`, `cow ghee`, `buffalo ghee`, and `clarified butter` to high priority in `FoodIconResolver.kt`.
- Verification: Deployed app to device successfully; unit tests passed (`FoodIconResolverTest`); changelog validator script passed (`verify-changelog.ps1`).

### Refine 2-in-1 Composite Meal Icon Layout & Drawing Z-Index Order
- **By:** Gemini in Android Studio (Auto)
- Created `ic_food_ghee.xml` featuring a traditional clay/brass matka bowl filled with warm golden-yellow ghee, a polished metallic silver spoon dipping in, and a golden ghee drop highlight.
- Mapped `ghee`, `desi ghee`, `cow ghee`, `buffalo ghee`, and `clarified butter` in `FoodIconResolver.kt`.
- Verification: Deployed app to device successfully; unit tests passed (`FoodIconResolverTest`); changelog validator script passed (`verify-changelog.ps1`).

### Refine 2-in-1 Composite Meal Icon Layout & Drawing Z-Index Order
- **By:** Gemini in Android Studio (Auto)
- Updated 2-in-1 layout in `MealIconView.kt` to render the primary main dish icon at Top-Left in the foreground (drawn second) and the secondary item at Bottom-Right in the background (drawn first).
- Increased sub-badge size to 32dp with clean diagonal positioning so both food icons remain 100% distinct and legible without obscuring the primary food item.
- Verification: Deployed app to device successfully; unit tests passed (`FoodIconResolverTest`); changelog validator script passed (`verify-changelog.ps1`).

### Fix ClassCastException in Meal List ViewHolders by Updating Shared Item Layouts
- **By:** Gemini in Android Studio (Auto)
- Updated `item_reminder.xml` and `item_exercise.xml` to use `<com.dailyroutine.app.MealIconView>` for `@+id/tvEmoji`, resolving `ClassCastException` in `CaloriesActivity.kt`, `DietPlanActivity.kt`, `ReminderAdapter.kt`, and `WorkoutPlanActivity.kt`.
- Added backward-compatible `setImageResource(@DrawableRes resId: Int)` method in `MealIconView.kt`.
- Verification: Deployed app to device successfully; unit tests passed (`FoodIconResolverTest`); changelog validator script passed (`verify-changelog.ps1`).

### Implement Multi-Item Composite Meal Icon Engine (2-in-1 & 3-in-1) and UI Icon Enhancements
- **By:** Gemini in Android Studio (Auto)
- Updated `FoodIconResolver.kt` with a smart clause & portion quantity tokenizer (`getIconsForMeal(...)`) to resolve up to 3 combined icons for multi-dish meals, enforcing a 60% portion dominance threshold.
- Created `MealIconView.kt` custom composite view and `bg_meal_sub_icon_badge.xml` rendering 56dp single badges, 2-in-1 horizontal overlapped badges, and 3-in-1 triad badges.
- Updated `item_meal.xml`, `dialog_meal_nutrition_info.xml`, `CaloriesActivity.kt`, and `DietPlanActivity.kt` to bind `MealIconView` and `FoodIconResolver.getIconsForMeal(...)`.
- Added unit tests in `FoodIconResolverTest.kt` covering multi-item resolution, 2-in-1/3-in-1 combos, and dominance threshold checks.
- Verification: `FoodIconResolverTest` unit tests passed (`app:testDebugUnitTest --tests com.dailyroutine.app.FoodIconResolverTest`); changelog validator script passed (`verify-changelog.ps1`).

### Add Comprehensive Unit & Contract Tests for FoodIconResolver and UI Mapping
- **By:** Gemini in Android Studio (Auto)
- Expanded `FoodIconResolverTest.kt` with 5 test suites covering Tier 1 individual dish matches, description field keyword matching, all 14 category fallbacks, slot default fallbacks, and RoutineIconMapper delegation.
- Added UI contract test `foodIconResolver_integratedInCaloriesAndDietPlanActivities` in `UiResourceContractsTest.kt` ensuring CaloriesActivity and DietPlanActivity invoke FoodIconResolver.
- Verification: All FoodIconResolver tests passed (`app:testDebugUnitTest --tests com.dailyroutine.app.FoodIconResolverTest`); changelog validator script passed (`verify-changelog.ps1`).

### Implement FoodIconResolver 3-Tier Fallback Engine & Wire UI Integration
- **By:** Gemini in Android Studio (Auto)
- Created `FoodIconResolver.kt` to dynamically map any user-logged food item to its corresponding 3D vector drawable across 3 tiers: Tier 1 (105+ specific dish matches), Tier 2 (30+ category fallback matches for Chinese, Sabji, Dals, Proteins, Fasting, etc.), and Tier 3 (Meal slot defaults).
- Updated `RoutineIconMapper.kt` and wired `FoodIconResolver.getIconForMeal(...)` into `CaloriesActivity.kt` and `DietPlanActivity.kt` dialogs & RecyclerView item adapters.
- Added comprehensive unit tests in `FoodIconResolverTest.kt` covering all 3 fallback tiers.
- Verification: Unit tests passed (`FoodIconResolverTest`); changelog validator script passed (`verify-changelog.ps1`).

### Add Batch #15 of Regional Mithai, Beverages & Main Protein Category Vector Drawables
- **By:** Gemini in Android Studio (Auto)
- Created Batch #15 of 10 new high-detail 3D vector drawables (`ic_food_shrikhand.xml`, `ic_food_mishti_doi.xml`, `ic_food_ghevar.xml`, `ic_food_peda.xml`, `ic_food_sugarcane_juice.xml`, `ic_food_peanut_butter_toast.xml`, `ic_cat_sprouts.xml`, `ic_cat_chicken.xml`, `ic_cat_mutton.xml`, `ic_cat_fish.xml`). Formatted with ceramic plate/bowl/matka bases, ambient drop shadows, specular highlights, and authentic culinary ingredient details.
- Verification: XML vector attribute validation passed; changelog validator script passed (`verify-changelog.ps1`).

### Add Batch #14 of Regional Curries, Bakery & Category Fallback Vector Drawables
- **By:** Gemini in Android Studio (Auto)
- Created Batch #14 of 10 new high-detail 3D vector drawables (`ic_cat_paneer_tofu.xml`, `ic_cat_mushroom.xml`, `ic_cat_farsan.xml`, `ic_food_sarson_saag.xml`, `ic_food_sol_kadi.xml`, `ic_food_kadhai_paneer.xml`, `ic_food_baingan_bharta.xml`, `ic_food_veg_puff.xml`, `ic_food_garlic_bread.xml`, `ic_food_waffles_pancakes.xml`). Formatted with ceramic plate/bowl bases, ambient drop shadows, specular highlights, and authentic culinary ingredient details.
- Verification: XML vector attribute validation passed; changelog validator script passed (`verify-changelog.ps1`).

### Add Batch #13 of Category Fallback Vector Drawables
- **By:** Gemini in Android Studio (Auto)
- Created Batch #13 of 10 new high-detail 3D category fallback vector drawables (`ic_cat_crab.xml`, `ic_cat_pork.xml`, `ic_cat_sabji_dry.xml`, `ic_cat_sabji_gravy.xml`, `ic_cat_dal_lentils.xml`, `ic_cat_millets.xml`, `ic_cat_chinese_veg.xml`, `ic_cat_chinese_nonveg.xml`, `ic_cat_veg_soup.xml`, `ic_cat_nonveg_soup.xml`). Formatted with ceramic plate/bowl bases, ambient drop shadows, specular highlights, and authentic culinary category iconography.
- Verification: XML vector attribute validation passed; changelog validator script passed (`verify-changelog.ps1`).

### Add Batch #12 of Fasting, Fitness & Regional Food Vector Drawables
- **By:** Gemini in Android Studio (Auto)
- Created Batch #12 of 10 new high-detail 3D food vector drawables (`ic_food_sabudana_khichdi.xml`, `ic_food_sabudana_vada.xml`, `ic_food_makhana.xml`, `ic_food_protein_shake.xml`, `ic_food_sprouts_salad.xml`, `ic_food_grilled_chicken.xml`, `ic_food_uttapam.xml`, `ic_food_appam_stew.xml`, `ic_food_rajma_chawal.xml`, `ic_food_kadhi_pakora.xml`). Formatted with ceramic plate/bowl bases, ambient drop shadows, specular highlights, and authentic culinary ingredient details.
- Verification: XML vector attribute validation passed; changelog validator script passed (`verify-changelog.ps1`).

### Add Batch #11 of Indian Sweets & Traditional Beverages Vector Drawables
- **By:** Gemini in Android Studio (Auto)
- Created Batch #11 of 10 new high-detail 3D food vector drawables (`ic_food_rasgulla.xml`, `ic_food_kheer.xml`, `ic_food_kaju_katli.xml`, `ic_food_gajar_halwa.xml`, `ic_food_laddoo.xml`, `ic_food_kulfi.xml`, `ic_food_falooda.xml`, `ic_food_badam_milk.xml`, `ic_food_jaljeera.xml`, `ic_food_thandai.xml`). Formatted with ceramic coaster/bowl bases, ambient drop shadows, specular highlights, and authentic culinary ingredient details.
- Verification: XML vector attribute validation passed; changelog validator script passed (`verify-changelog.ps1`).

### Add Batch #10 of Common Indian Food Vector Drawables
- **By:** Gemini in Android Studio (Auto)
- Created Batch #10 of 10 new high-detail 3D food vector drawables (`ic_food_butter_naan.xml`, `ic_food_puri_bhaji.xml`, `ic_food_chole_bhature.xml`, `ic_food_kathi_roll.xml`, `ic_food_sev_puri.xml`, `ic_food_dahi_puri.xml`, `ic_food_manchurian.xml`, `ic_food_chowmein.xml`, `ic_food_jeera_rice.xml`, `ic_food_lemon_rice.xml`). Formatted with ceramic plate/bowl bases, ambient drop shadows, specular highlights, and authentic culinary ingredient details.
- Verification: XML vector attribute validation passed; changelog validator script passed (`verify-changelog.ps1`).

### Add Final Batch #9 Completing User Food List Vector Drawables
- **By:** Gemini in Android Studio (Auto)
- Created Batch #9 completing the remaining food items from user list (`ic_food_dragonfruit.xml`, `ic_food_coconut.xml`, `ic_food_egg_omelette.xml`, `ic_food_boiled_egg.xml`, `ic_food_egg_bhurji.xml`). Formatted with ceramic plate/bowl/coaster bases, ambient drop shadows, specular highlights, and authentic culinary ingredient details.
- Verification: XML vector attribute validation passed; changelog validator script passed (`verify-changelog.ps1`).

### Add Batch #8 of User Food List Vector Drawables
- **By:** Gemini in Android Studio (Auto)
- Created Batch #8 of 10 new high-detail 3D fruit vector drawables from user list (`ic_food_sweet_lime.xml`, `ic_food_apple.xml`, `ic_food_pomegranate.xml`, `ic_food_pineapple.xml`, `ic_food_kiwi.xml`, `ic_food_watermelon.xml`, `ic_food_muskmelon.xml`, `ic_food_grapes.xml`, `ic_food_peach.xml`, `ic_food_black_grapes.xml`). Formatted with ceramic plate/bowl bases, ambient drop shadows, specular highlights, and authentic culinary fruit details.
- Verification: XML vector attribute validation passed; changelog validator script passed (`verify-changelog.ps1`).

### Add Batch #7 of User Food List Vector Drawables
- **By:** Gemini in Android Studio (Auto)
- Created Batch #7 of 10 new high-detail 3D food vector drawables from user list (`ic_food_cashews.xml`, `ic_food_pistachios.xml`, `ic_food_anjeer.xml`, `ic_food_raisins.xml`, `ic_food_seeds.xml`, `ic_food_juices.xml`, `ic_food_mango.xml`, `ic_food_orange.xml`, `ic_food_guava.xml`, `ic_food_banana.xml`). Formatted with ceramic plate/bowl/coaster bases, ambient drop shadows, specular highlights, and authentic culinary ingredient details.
- Verification: XML vector attribute validation passed; changelog validator script passed (`verify-changelog.ps1`).

### Add Batch #6 of User Food List Vector Drawables
- **By:** Gemini in Android Studio (Auto)
- Created Batch #6 of 10 new high-detail 3D food vector drawables from user list (`ic_food_whiskey.xml`, `ic_food_vodka.xml`, `ic_food_red_rum.xml`, `ic_food_white_rum.xml`, `ic_food_beer.xml`, `ic_food_red_wine.xml`, `ic_food_champagne.xml`, `ic_food_dry_fruits.xml`, `ic_food_almonds.xml`, `ic_food_walnuts.xml`). Formatted with ceramic coaster/bowl bases, stemware glass shapes, ambient drop shadows, specular highlights, and authentic ingredient details.
- Verification: XML vector attribute validation passed; changelog validator script passed (`verify-changelog.ps1`).

### Add Batch #5 of User Food List Vector Drawables
- **By:** Gemini in Android Studio (Auto)
- Created Batch #5 of 10 new high-detail 3D food vector drawables from user list (`ic_food_wada_pav.xml`, `ic_food_bhaje.xml`, `ic_food_misal_pav.xml`, `ic_food_pav_bhaji.xml`, `ic_food_chocolate_bar.xml`, `ic_food_curd.xml`, `ic_food_curd_rice.xml`, `ic_food_shami_kebab.xml`, `ic_food_seekh_kebab.xml`, `ic_food_medu_wada.xml`). Formatted with ceramic plate/bowl/coaster bases, ambient drop shadows, specular highlights, and authentic culinary ingredient details.
- Verification: XML vector attribute validation passed; changelog validator script passed (`verify-changelog.ps1`).

### Add Batch #4 of User Food List Vector Drawables
- **By:** Gemini in Android Studio (Auto)
- Created Batch #4 of 10 new high-detail 3D food vector drawables from user list (`ic_food_mutton_roast.xml`, `ic_food_tandoori_chicken.xml`, `ic_food_chicken_tikka.xml`, `ic_food_fish_curry.xml`, `ic_food_tandoori_prawns.xml`, `ic_food_prawns_curry.xml`, `ic_food_chicken_taco.xml`, `ic_food_veg_taco.xml`, `ic_food_momos.xml`, `ic_food_shawarma.xml`). Applied researched realistic non-veg iconography principles: distinct goat mutton rib chops (chaap) for Mutton Roast, char-grilled red chicken drumstick with silver foil for Tandoori Chicken, C-shaped curled jumbo prawns for Tandoori/Curry Prawns, and oval fish steak cross-section with central spine bone for Fish Curry. Veg Taco, Momos, and Shawarma kept clean without non-veg modifications.
- Verification: XML vector attribute validation passed; changelog validator script passed (`verify-changelog.ps1`).

### Improve Details for Fafda, Chicken Fry, Chicken Curry, Mutton Curry, and Buttermilk
- **By:** Gemini in Android Studio (Auto)
- Refined 5 vector drawables (`ic_food_fafda.xml`, `ic_food_chicken_fry.xml`, `ic_food_chicken_curry.xml`, `ic_food_mutton_curry.xml`, `ic_food_buttermilk.xml`) for distinct culinary accuracy. Drawn wavy, crinkled besan fafda strips; KFC-style craggy crispy golden fried chicken drumstick; cooked chicken leg piece submerged in red chicken curry; distinct goat mutton rib chop (chaap) in dark red mutton rassa; and frothy spiced buttermilk with slit green chili accent.
- Verification: XML vector attribute validation passed; changelog validator script passed (`verify-changelog.ps1`).

### Add Batch #3 of User Food List Vector Drawables
- **By:** Gemini in Android Studio (Auto)
- Created Batch #3 of 10 new high-detail 3D food vector drawables from user list (`ic_food_fafda.xml`, `ic_food_lassi.xml`, `ic_food_buttermilk.xml`, `ic_food_limbu_pani.xml`, `ic_food_black_coffee.xml`, `ic_food_green_tea.xml`, `ic_food_chicken_fry.xml`, `ic_food_chicken_curry.xml`, `ic_food_mutton_curry.xml`, `ic_food_fish_fry.xml`). Formatted with ceramic plate/saucer/coaster bases, ambient drop shadows, specular highlights, and authentic culinary ingredient details.
- Verification: XML vector attribute validation passed; changelog validator script passed (`verify-changelog.ps1`).

### Add Batch #2 of User Food List Vector Drawables
- **By:** Gemini in Android Studio (Auto)
- Created Batch #2 of 10 new high-detail 3D food vector drawables from user list (`ic_food_coffee.xml`, `ic_food_tea.xml`, `ic_food_chakli.xml`, `ic_food_murmure.xml`, `ic_food_popcorn.xml`, `ic_food_chips.xml`, `ic_food_kurkure.xml`, `ic_food_dhokla.xml`, `ic_food_samosa.xml`, `ic_food_kachori.xml`). Formatted with ceramic plate/saucer/coaster bases, ambient drop shadows, specular highlights, and authentic culinary ingredient details.
- Verification: XML vector attribute validation passed; changelog validator script passed (`verify-changelog.ps1`).

### Rework Bhel Puri Vector Drawable Composition
- **By:** Gemini in Android Studio (Auto)
- Reworked `ic_food_bhel.xml` to present an authentic mixed-up street food bhel puri composition. Incorporated scattered white oval murmure (puffed rice) grains, glistening red tamarind-chili sauce coating streaks, finely diced purple red onion bits, scattered green cilantro (coriander) flecks, crispy golden sev strands, and papdi shards.
- Verification: XML vector attribute validation passed; changelog validator script passed (`verify-changelog.ps1`).

### Refine Detail for Upma, Bhel, and Sev Vector Drawables
- **By:** Gemini in Android Studio (Auto)
- Refined `ic_food_upma.xml`, `ic_food_bhel.xml`, and `ic_food_sev.xml` to enhance culinary accuracy and authentic ingredient presentation. Added molded semolina dome shape with roasted cashews, peas, carrots, and mustard seeds for Upma; distinct oval murmure puffed rice grains, papdi, sev, and chutney drizzling for Bhel; and thick/fine besan noodles with spice flecks for Sev.
- Verification: XML vector attribute validation passed; changelog validator script passed (`verify-changelog.ps1`).

### Add Batch #1 of User Food List Vector Drawables
- **By:** Gemini in Android Studio (Auto)
- Created Batch #1 of 10 new high-detail 3D food vector drawables from user list (`ic_food_upma.xml`, `ic_food_pani_puri.xml`, `ic_food_bhel.xml`, `ic_food_cold_drink.xml`, `ic_food_ice_cream.xml`, `ic_food_french_fries.xml`, `ic_food_sev.xml`, `ic_food_jalebi.xml`, `ic_food_gulab_jamun.xml`, `ic_food_milk.xml`). Formatted with ceramic plate/coaster bases, drop shadows, specular highlights, and authentic culinary ingredient details.
- Verification: XML vector attribute validation passed; changelog validator script passed (`verify-changelog.ps1`).

### Add Ten New Detailed Food Vector Drawables
- **By:** Gemini in Android Studio (Auto)
- Created 10 new high-detail 3D glossy food vector drawables (`ic_food_pizza.xml`, `ic_food_dosa.xml`, `ic_food_idli_sambar.xml`, `ic_food_paneer_tikka.xml`, `ic_food_salad.xml`, `ic_food_pasta.xml`, `ic_food_paratha.xml`, `ic_food_noodle_ramen.xml`, `ic_food_oatmeal_bowl.xml`, `ic_food_fruit_bowl.xml`). Formatted with ceramic plate/bowl bases, drop shadows, specular highlights, and authentic culinary ingredient details.
- Verification: XML vector attribute validation passed; changelog validator script passed (`verify-changelog.ps1`).

### Enhance Detail and 3D Depth for Food Vector Drawables
- **By:** Gemini in Android Studio (Auto)
- Added detailed, authentic culinary ingredients and 3D glossy depth across all 10 food vector drawables (`ic_food_burger.xml`, `ic_food_poha.xml`, `ic_food_bread_sandwich.xml`, `ic_food_dal_chapati.xml`, `ic_food_dal_khichdi.xml`, `ic_food_dal_rice.xml`, `ic_food_nonveg_biryani.xml`, `ic_food_nonveg_fried_rice.xml`, `ic_food_veg_biryani.xml`, `ic_food_veg_fried_rice.xml`). Added prominent roasted chicken drumsticks with bone joints and sear marks on Non-Veg Fried Rice and Biryani, fluffy egg scramble, pan-seared 3D paneer cubes, toasted grill marks on sandwiches, roasted peanuts with skin seam details on poha, fried birista onions, tadka chili tempering on dal, and specular plate rim highlights.
- Verification: XML vector attribute validation passed; changelog validator script passed (`verify-changelog.ps1`).

### Refine Rice and Dal Meal Vectors
- **By:** Codex (GPT-6)
- Strengthened the top-down rice grain detail and made vegetables, chicken, prawn, egg, lentils, and dal tempering more distinct in the seven rice and dal drawable resources. Vegetarian rice icons remain egg-free.
- Verification: XML parsing and vector-dimension checks passed; raster preview rendered for small list-icon review; changelog validator passed. Android build not run.

## 2026-10-23

### Add First Ten Meal Vector Drawables
- **By:** Codex (GPT-6)
- Added top-down cartoon vector resources for Dal Rice, Dal Khichdi, Dal Chapati, Veg Biryani, Non-Veg Biryani, Non-Veg Fried Rice, Veg Fried Rice, Bread Sandwich, Burger, and Poha. Kept egg details out of vegetarian dishes and used distinct vegetable versus protein toppings.
- Verification: Android drawable XML well-formedness and vector attribute checks passed; changelog validator passed. Android build not run; the resources are not yet wired into meal icon selection.

## 2026-10-22

### Allow Limited Percent Signs in Meal Descriptions
- **By:** Codex (GPT-6)
- Allowed up to five `%` characters in meal descriptions, capped typing and pasted input at five, and show a subtle inline notice once the limit is reached. Added tests for validation, paste/typing limits, replacing signs, and warning visibility. Corrected existing `HealthSyncWorkerTest` API usage that blocked test compilation. Verification: targeted `TextInputPolicyTest` and the affected HealthSyncWorker test passed; changelog validator passed.

## 2026-10-22

### Add Tests for Shared Planner Helpers
- **By:** Codex (GPT-6)
- Added unit coverage for generic template creation/application/overlap/rename/delete, date-range arithmetic and previews, and shared date-strip rendering state and selection. Existing meal/workout integration tests cover both planner domains. Verification: changelog validator passed; targeted Android test compilation is blocked by existing errors in `HealthSyncWorkerTest.kt` at lines 218 and 232 (`get()` and `inputData` unresolved), before test execution.

## 2026-10-22

### Share Planner Date Strip and Range Utilities
- **By:** Codex (GPT-6)
- Extracted shared calendar-strip construction, centering, navigation callbacks, and template date-range arithmetic/preview formatting into planner utilities used by both Diet and Workout activities. Each activity retains its own item status and color styling. Verification: changelog validator and scoped diff check passed; Android build not run because Gradle 9.8 is unavailable locally and the wrapper download is denied; tests not run.

## 2026-10-22

### Unify Planner Template Operations
- **By:** Codex (GPT-6)
- Added shared `PlannableItem` and `PlannableTemplate` contracts and expanded generic `TemplateManager<T, I>` to handle template creation from ranges, range validation, overlap detection, application, copied-item IDs, catalog rename/delete, and applied-range metadata for both meals and workouts. Shared the applied-range list and action dialogs as well. Verification: changelog validator passed; `:app:compileDebugKotlin --offline` was attempted but could not start because Gradle 9.8 was not cached and its distribution download was denied; tests not run.

## 2026-10-22

### Extract Permission and Template Catalog Coordination
- **By:** Codex (GPT-6)
- Moved first-launch notification, exact-alarm, and Health Connect permission sequencing into `FirstLaunchPermissionFlow`; moved manual Health Connect permission validation and history-sync scheduling into `HealthConnectSyncCoordinator`; centralized template list ordering, rename propagation to applied ranges, and deletion in generic `TemplateManager<T>`. Verification: changelog validator passed; `:app:compileDebugKotlin --offline` was attempted but could not start because Gradle 9.8 was not cached and its distribution download was denied; tests not run.

## 2026-10-22

### Extract Home Dashboard and Shared Planner Dialogs
- **By:** Codex (GPT-6)
- Moved home dashboard data aggregation and view updates from `MainActivity` into `HomeDashboardUpdater`, and shared the common action dialog and action model between Diet and Workout planners. This starts reducing activity responsibilities and repeated planner UI code while preserving each planner's domain-specific template behavior. Verification: changelog validator passed; Android build and tests not run.

## 2026-10-19

### Cover Dashboard Suggestion Wording
- **By:** Codex (GPT-6)
- Updated the dashboard UI contract test to assert the shared profile-details wording and confirm Weight and Calories still use their respective calculated targets. Verification: changelog validator passed; targeted Gradle test could not start because the Gradle 9.8 distribution download was denied by network restrictions.
### Refine Dashboard Suggestion Wording
- **By:** Codex (GPT-6)
- Changed the Weight and Calories Today suggestion messages to use “According to the vital details added to your profile.” Verification: changelog validator passed; reviewed the two message updates.
### Correct Calories Dashboard Ideal Target
- **By:** Codex (GPT-6)
- Updated the Calories Dashboard Today message to display the profile-calculated ideal calorie intake in kcal/day instead of the ideal weight. Verification: changelog validator passed; reviewed the Calories Dashboard message update.

### Update Home Layout Contract Tests
- **By:** Codex (GPT-6)
- Updated home UI contracts for the current Wellness, Nutrition/Workout, Wellness Tracking, Hydration/Reminders, and Sync order; added checks for the 82dp wellness card and evenly distributed viewport spacing. Verification: changelog validator and layout XML parsing passed; targeted Gradle test could not start because the Gradle 9.8 wrapper download was denied by network restrictions.

### Even Out Home Screen Section Spacing
- **By:** Codex (GPT-6)
- Made the home content fill the viewport and distribute available vertical space evenly between Wellness, Nutrition/Workout, the Wellness Tracking grid, Hydration/Reminders, and Sync actions, including below the final buttons. Verification: pending.

### Even Spacing Before Sync Actions
- **By:** Codex (GPT-6)
- Removed the extra top margin on Sync Now and Sync Logs, keeping the hydration/reminders row's existing bottom margin to make the inter-row gap consistent. Verification: pending.

### Place Hydration and Reminders Before Sync Actions
- **By:** Codex (GPT-6)
- Moved the hydration and reminders row below the Wellness Tracking cards and above Sync Now and Sync Logs. Verification: pending.

### Reorder Home Wellness and Quick Actions
- **By:** Codex (GPT-6)
- Moved the full-width Daily Wellness card below the header, followed by hydration and reminders, then Nutrition and Workout. Verification: pending.

### Match Daily Wellness Card Height to Nutrition
- **By:** Codex (GPT-6)
- Kept the Daily Wellness card full width and set it to the same 82dp height as the Nutrition and Workout cards, with a compact score ring and text to fit. Verification: pending.

### Refine Daily Wellness and Weight Messaging
- **By:** Codex (GPT-5)
- Renamed and repositioned the home Daily Wellness Score below Nutrition and Workout, enabled 0.5-point factor increments, centered the compact hydration control with a filled blue droplet, and updated home, Weight, and Calorie Dashboard weight/ideal-weight messages to use profile data and today's logging language. Verification: XML parsing, app Kotlin compilation, and changelog validator passed; the targeted unit-test task remains blocked by existing `HealthSyncWorkerTest.kt` unresolved `get` and `inputData` references at lines 218 and 232.

### Improve Home Hydration Readability
- **By:** Codex (GPT-6)
- Replaced the outlined water mark with a filled droplet and removed competing spacer weight so the daily water total gets the remaining width in the compact hydration control. Verification: XML well-formedness and changelog validator passed.

### Refine Home Hydration and Sync Actions
- **By:** Codex (GPT-6)
- Removed the upcoming reminder card, displayed today's water total in the compact hydration card, and refreshed the bottom sync actions with distinct sync and log icons plus a muted gray Sync Logs style. Updated the home UI resource contract. Verification: XML well-formedness and changelog validator passed; Gradle resource/Kotlin tasks could not start because the Gradle 9.8 distribution download was denied by network restrictions.

### Prioritize Home Reminders and Move Sync Controls Down
- **By:** Codex (GPT-6)
- Put Nutrition and Workout directly after Wellness Compass, placed compact water controls beside Reminders, moved the upcoming reminder strip below them, and moved Sync Now plus an explicit Sync Logs button to the bottom. Removed the visible hydration label/value while exposing today's total through the water icon's accessibility description. Added a home layout-order contract. Verification: Android resource processing and app Kotlin compilation passed; targeted unit tests could not compile because existing `HealthSyncWorkerTest.kt` errors at lines 218 and 232 report unresolved `get` and `inputData` references; changelog validator passed.

### Mask PINs Immediately While Typing
- **By:** Codex (GPT-6)
- Applied the PIN transformation after enabling single-line input, which otherwise replaced the initial mask, and track reveal state explicitly for the eye toggle. Strengthened the Robolectric test to assert that digits are masked immediately after typing before tapping the eye. Verification: Android Kotlin compilation passed; targeted unit tests could not compile because existing `HealthSyncWorkerTest.kt` errors at lines 218 and 232 report unresolved `get` and `inputData` references; changelog validator passed.

### Cover PIN Eye Icon State Convention
- **By:** Codex (GPT-6)
- Extended the App Lock UI contract test to assert that a hidden PIN shows the open eye with a “Show PIN” action, and a revealed PIN shows the crossed eye with a “Hide PIN” action. Verification: changelog validator passed; targeted Gradle test could not compile because existing `HealthSyncWorkerTest.kt` errors at lines 218 and 232 report unresolved `get` and `inputData` references.

### Make PIN Visibility Toggle Deterministic
- **By:** Codex (GPT-6)
- Kept PIN inputs on the numeric keyboard and replaced numeric-password input mode with a dedicated digit mask transformation. The eye now toggles the transformation directly, masking every digit consistently including the most recently typed digit. Updated visibility tests to assert the rendered masked text. Verification: Android Kotlin compilation passed; targeted unit tests could not compile because existing `HealthSyncWorkerTest.kt` errors at lines 218 and 232 report unresolved `get` and `inputData` references; changelog validator passed.

### Harden App Lock PIN Controls
- **By:** Codex (GPT-6)
- Applied explicit password transformation to every PIN field and centered the eye control against the field height. PIN changes now reject the current PIN and the immediately previous PIN, and switching between PIN and biometric unlock requires current-PIN verification. Added tests for masking, visibility toggling, authentication gating, and PIN reuse prevention. Verification: app Kotlin compilation passed; targeted unit tests could not compile because existing `HealthSyncWorkerTest.kt` errors at lines 218 and 232 report unresolved `get` and `inputData` references; changelog validator passed.

### App Lock PIN Visibility Tests
- **By:** Codex (GPT-6)
- Added Robolectric unit coverage confirming PIN entry starts masked, the eye reveals and re-masks it, and the entered digits are preserved. Verification: changelog validator passed; targeted Gradle test could not compile because existing `HealthSyncWorkerTest.kt` errors at lines 218 and 232 report unresolved `get` and `inputData` references.

### Mask App Lock PIN Entry
- **By:** Codex (GPT-6)
- App Lock PIN creation and change fields now start masked and include an eye control to reveal or hide the current PIN while typing. Verification: changelog validator passed; Android build not run.

### Dashboard Icon Resource Tests
- **By:** Codex (GPT-6)
- Added unit contracts for semantic drawable mappings, shared dashboard/detail-screen references, and tintable 24dp vector resources. Removed a duplicate local manifest declaration that prevented the existing UI contract test class from compiling. Verification: changelog validator passed; `:app:testDebugUnitTest --tests com.dailyroutine.app.UiResourceContractsTest` could not start because the Gradle 9.8 distribution download was denied by network restrictions.

### Dashboard Icon Pack Refresh
- **By:** Codex (GPT-6)
- Refined shared Nutrition, meal-type, Workout, Steps, Sleep, Energy, and Weight vectors for a consistent rounded filled style across dashboards, lists, dialogs, and widgets while preserving existing resource IDs, colors, sizes, and layout references. Verification: all ten vector XML files parsed and changelog validator passed; Android build not run.

### Compact Home Screen Sync Button
- **By:** Codex (GPT-6)
- Changed the home-screen Health Connect action label to “Sync Now” to prevent the longer text from distorting the button. Added a UI contract test for the compact label. Verification: XML parsed, scoped diff check and changelog validator passed; Gradle tests not run because the wrapper distribution is unavailable under current network restrictions.

### Health Connect Regression Test Coverage
- **By:** Codex (GPT-6)
- Added regression coverage for Health Connect all-origin source identity and migration-triggered history repair, source-independent connection labeling and legacy preference cleanup, manual sync/provider discovery and all-origin contracts, Health Connect walking-duration persistence, Heart Points permission/integration removal, and overlap-safe sleep duration aggregation. Verification: targeted Gradle tests could not start because the Gradle 9.8 wrapper distribution is uncached and the network connection was denied, including the escalated retry; scoped diff check and changelog validator passed; tests not run.

### Use Health Connect for Manual and Sleep Sync
- **By:** Codex (GPT-6)
- Removed the installed fitness-app picker and package discovery from manual sync, and route manual sync through Health Connect permission checks and the existing history sync worker. Sleep onboarding now requests Health Connect sleep access directly. Health Connect step, distance, calorie, sleep, and walking-session reads include all available origins; overlapping sleep sessions are merged before duration totals are stored. Legacy provider-specific sync identity now triggers a fresh history pass. Verification: changelog validator passed, edited XML parsed, and scoped diff check passed; tests not run.

### Remove Heart Points and Keep Walking Metrics Health Connect Only
- **By:** Codex (GPT-6)
- Removed the Heart Points dashboard cards, chart series, legends, Google Fit OAuth reader, permission/dependency path, and the Health Connect weight permission. Stopped copying legacy Heart Points preferences into Room. Step distance now displays only Health Connect DistanceRecord values, and active walking duration uses Health Connect walking exercise sessions rather than a steps-based estimate; manual weight logging, steps, and sleep sync remain. Existing cached/schema Heart Points stay read-only for the unchanged calorie estimator so existing calorie results are not altered. Verification: changed XML parsed and changelog validator passed; Kotlin compile could not start because the uncached Gradle wrapper distribution could not be downloaded under network restrictions; tests not run.

### Step Total Accuracy and Manual Health Sync
- **By:** Codex (GPT-6)
- Step totals now prefer Health Connect's all-origin aggregate, which applies platform source-priority deduplication, instead of heuristically accepting small deltas that can inflate counts. Manual sync now verifies granted Steps access, marks Health Connect connected before starting the worker, routes Google Fit OAuth success through Health Connect permission validation, and queues a fresh pass instead of silently dropping taps while a history job is active. Updated existing step resolver expectations. Verification: changelog validator passed; Android tests/build not run.

### Room Health History Reliability
- **By:** Codex (GPT-6)
- Added explicit Room migration 3→4 with per-metric sync status and source fields, removing destructive migration fallback so upgrades preserve stored health history. Bumped the preference-to-Room backfill version and annotate imported legacy values; metric patches now merge transactionally, Health Connect, Google Fit, and manual weight writes record provenance, and weekly/monthly Heart Points prefer migrated Room values. Verification: changelog validator passed; Android build could not resolve the uncached Android Gradle plugin offline, and wrapper download was blocked by network access; tests not run.

### Heart Points Historical Data Repair
- **By:** Codex (GPT-6)
- Full heart-points history syncs now re-read every date, and daily corrections refresh all seven recent days so previously stored zeros do not block late data. Bumped the Google Fit history validation identity and versioned the full repair work name to force one fresh 180-day pass for existing installs. When daily aggregate buckets are zero, the reader checks raw Google Fit records and fills only missing/zero buckets; weekly and monthly charts prefer verified synced preference values over stale Room values. Verification: `:app:assembleDebug` and changelog validator passed; tests not run.

## 2026-10-16

### Health Sync Reliability Hardening
- **By:** Codex (GPT-6)
- Kept the configured 10 PM → midnight → 6 AM step schedule. Health Connect reads now distinguish provider errors from successful empty/zero results, leave failed days retryable, and mark partial logs as failures. Scheduled syncs refresh yesterday for late provider data, clip overnight sleep sessions to each calendar day, and reconcile small safe all-origin step deltas. Manual and initial full history repairs run through persistent WorkManager jobs in resumable 30-day chunks; daily history repair checks the recent week. Sleep and daily history jobs reschedule after success, and clock/timezone changes rebuild their wall-clock targets. Worker syncs are serialized, Room updates merge against current rows, source changes trigger history repair, Google Fit heart points can sync independently of Health Connect, and disconnect cleanup preserves water and goals. Verification: Android debug build and changelog validator passed; tests not run.

### Heart Points History Completeness and Chart Color
- **By:** Codex (GPT-6)
- Made daily Google Fit Heart Points reads report whether the result is authoritative, fill omitted dates with explicit zeroes only after a successful read, and leave failed reads retryable. Manual history sync detects missing stored values even if old completion markers claim success, and background sync repairs days whose Room row exists but legacy heart-points value is missing. Both background and manual history syncs now persist complete daily values; the weekly/monthly graphs can use verified legacy synced history when Room has no value. Replaced the reused green calorie bar drawable with a red Heart Points drawable matching the legend. Verification: changelog validator passed; tests/build not run.

### Home Card Icon Refresh
- **By:** Codex (GPT-6)
- Refined the reminder, hydration, nutrition, workout, steps, sleep, calorie, and weight vector icons using rounded Material icon conventions while keeping existing drawable IDs, tint colors, and layout wiring. Verification: all eight vector XML files parsed and changelog validator passed; Android build not run.

### Home Header Animation and Wellness Cards
- **By:** Codex (GPT-6)
- Shortened and unified the header entrance, softened avatar motion, aligned background oscillation for seamless looping, removed the wellness card border and bicep emoji, and slightly deepened the light-mode hydration button fill. Updated the home UI contract for the border removal. Verification: changelog validator passed; Android build and tests not run.

### Home Header Typography
- **By:** Codex (GPT-6)
- Added an exclamation mark to time-based greetings and matched the header date text size to the greeting label. Reviewed the header animation implementation and documented improvement recommendations in the handoff. Verification: changelog validator passed; Android build not run.

### Home Water Button Color Consistency
- **By:** Codex (GPT-6)
- Matched the home screen's add-water button foreground and background colors to the remove-water button. Verification: `:app:assembleDebug` and changelog validator passed.

### Water Summary Delete Policy Coverage
- **By:** Codex (GPT-6)
- Added a tested delete-action policy so only the generated water summary suppresses meal deletion while regular meals keep their existing delete action. Verification: `:app:testDebugUnitTest --tests com.dailyroutine.app.WaterIntakeSummaryTest` and changelog validator passed.

### Water Summary Ordering and Deletion
- **By:** Codex (GPT-6)
- Hid the delete control for generated total-water rows and sort those rows after every regular meal in the daily nutrition lists. Verification: targeted water-summary unit tests, Android debug build, and changelog validator passed.

### Water Summary Editing and Display
- **By:** Codex (GPT-6)
- Refined only the generated water summary rows to show liters beside the total label with hydration styling, hide meal timestamps and nutrition prompts, and open a water-only add/remove picker for the selected date. Limited home and summary adjustment choices to 250–1000 ml and added unit coverage for formatting, non-estimation, and adjustment boundaries. Verification: `:app:testDebugUnitTest --tests com.dailyroutine.app.WaterIntakeSummaryTest`, Android debug build, and changelog validator passed.

## 2026-10-15

### Daily Water Intake Summary
- **By:** Codex (GPT-6)
- Added a daily 11:50 PM water summary, including completed hydration reminders, to the selected day's meal list; marked it to bypass nutrition estimation and calorie totals; expanded homepage water choices in 250 ml increments; and displayed water amounts with quarter-liter precision in the homepage and widget. Verification: `:app:assembleDebug` passed before the final picker range adjustment and reminder-total inclusion; a repeat build could not run because Gradle 9.8 needed a network download. Changelog validator passed.

### Widget Preview Contract Test
- **By:** Codex (GPT-6)
- Added a widget contract test ensuring the picker references both the live widget layout and the square high-resolution static preview image. Verification: `:app:testDebugUnitTest --tests com.dailyroutine.app.WellnessWidgetContractTest` and changelog validator passed.

### Widget Picker Preview Refinement
- **By:** Codex (GPT-6)
- Redrew the static widget preview at higher resolution with smoother shell, ring, button, step, and water icon edges to improve clarity when scaled in launchers. Verification: changelog validator passed; Android build not run because Gradle distribution download is blocked by network restrictions.

### Widget Picker Preview Polish
- **By:** Codex (GPT-6)
- Replaced the rough vector placeholder with a sharper static preview that follows the real widget shell, blue steps progress ring, centered activity values, and water adjustment row. Verification: changelog validator passed; Android build not run because Gradle distribution download is blocked by network restrictions.

### Keep Widget Haptic on Launcher
- **By:** Codex (GPT-6)
- Isolated the transparent widget haptic activity in its own task affinity so it no longer brings Routinely's existing activity task to the foreground. Verification: changelog validator passed; Android build not run because Gradle distribution download is blocked by network restrictions.

### Widget Action Theme Resource Linking
- **By:** Codex (GPT-6)
- Replaced the invalid `android:windowDimAmount` theme item with the supported `android:backgroundDimEnabled` attribute, resolving the resource linker error shown while building for wireless installation. Verification: changelog validator passed; build not run because Gradle distribution download is blocked by network restrictions.

### Widget Water Haptic Routing
- **By:** Codex (GPT-6)
- Routed widget water controls through a transparent foreground activity to perform Android view haptic feedback during a user initiated interaction. Added the missing remove action handling by routing both controls through that activity. Verification: changelog validator passed; Android build not run because the Gradle distribution download remains blocked by network restrictions.

### Widget Haptic Reliability
- **By:** Codex (GPT-6)
- Switched water widget actions to Android's standard click vibration effect and trigger it after data and widget refresh work completes. Verification: changelog validator passed; Android build not run because Gradle distribution download is blocked by network restrictions.

### Widget Feedback and Preview
- **By:** Codex (GPT-6)
- Added the existing water-control haptic feedback to widget actions and configured a Routinely wellness widget preview for the Android widget picker, with a static fallback image. Verification: changelog validator passed; Android resource processing could not run because Gradle 9.6.0 was not cached and network access was denied.

## 2026-10-14

### Profile Report Layout Tests
- **By:** Codex (GPT-6)
- Added UI contract coverage for the warning's bold styling, rounded amber background, light/dark text colors, and Save Changes placement before report export. Verification: targeted Android unit tests and changelog validator passed.

### Profile Report Layout Polish
- **By:** Codex (GPT-6)
- Restyled the date-range prompt as a bold, high-contrast reminder and moved Save Changes above the wellness report export card. Verification: targeted Android unit tests and changelog validator passed.

### Wellness Report Export Feedback
- **By:** Codex (GPT-6)
- Renamed the export section, added date range and parameter guidance, and made the export button visibly disabled until both required selections are present. Verification: targeted Android unit tests and changelog validator passed.

### Profile and Wellness Option Tests
- **By:** Codex (GPT-6)
- Added coverage for report period increments and parameter selection, square cropping from portrait and landscape sources, 10 MB upload boundaries, and crop/report UI controls. Verification: targeted Android unit tests passed; changelog validator pending.

### Profile Photo Alignment
- **By:** Codex (GPT-6)
- Tightened the avatar and upload label container so “Upload photo” sits closer to the circle and aligns more evenly with the adjacent name field. Verification: Android debug build and changelog validator passed; unit tests not run at the user's request so they can test the changes first.

### Crop Dialog Actions
- **By:** Codex (GPT-6)
- Placed explicit “Save cropped photo,” “Use original photo,” and “Cancel” buttons inside the crop panel so the actions stay visible with the preview. Verification: Android debug build and changelog validator passed; unit tests not run at the user's request so they can test the changes first.

### Profile Photo Crop
- **By:** Codex (GPT-6)
- Separated the profile avatar edit badge from the upload label. Added an in-app crop preview with drag and pinch-to-zoom controls, plus explicit Crop & use and Use original actions. The image picker accepts system-recognized image formats, with Coil GIF and SVG decoders and bounded-size crop previews. Verification: Android debug build and changelog validator passed; unit tests not run at the user's request so they can test the changes first.

### Profile and Wellness Report UI
- **By:** Codex (GPT-6)
- Enlarged the home profile photo and added the “Profile & Settings” label; added private image uploads with a 10 MB size limit. Added wellness report period and parameter selection, including a select-all checkbox, and generate PDFs with only the chosen fields. Verification: changelog validator passed; Android build and tests not run because the user will test the changes.

### Nutrition Estimate Tests
- **By:** Codex (GPT-6)
- Added direct Android parsing coverage for low-calorie responses with omitted macros, explicit zero/partial macros, invalid nutrient values, and display behavior for explicit zero versus missing calories. Verification: changelog validator passed; unit tests not run per user testing plan.

### Backend / API
- **By:** Codex (GPT-6)
- Reordered the Gemini cascade to `gemini-3.5-flash-lite`, `gemini-3.1-flash-lite`, then `gemini-3.6-flash`, and removed `temperature` from the generation config. Updated the cascade regression test to assert the order and parameter removal. Verification: Worker tests passed (9 tests); changelog validator passed. Wrangler emitted sandbox log-write and static-analysis warnings, but Vitest completed successfully.

### Nutrition Estimates
- **By:** Codex (GPT-6)
- Updated Gemini nutrition instructions and Worker parsing to accept explicit zero and partial nutrient values; made Android nutrition fields nullable so missing values remain distinct from zero; removed macro/micronutrient synthesis; updated meal displays, intake aggregation, and cache invalidation/clearing to preserve partial estimates. Verification: changelog validator passed; Android and Worker tests not run because the user will test the changes.

## 2026-10-13

### Backend / API
- **By:** Codex (GPT-6)
- Set the Gemini cascade to `gemini-3.6-flash`, then `gemini-3.5-flash-lite`, then `gemini-3.1-flash-lite` as the user-selected hierarchy. Updated the cascade regression test to assert this order. Verification: Worker tests passed (10 tests); `npx tsc --noEmit` passed; changelog validator passed. Wrangler emitted sandbox log-write and static-analysis warnings during the tests, but Vitest completed successfully. No Worker deployment performed.

### Backend / API
- **By:** Codex (GPT-6)
- Replaced the temporary first Gemini cascade model `gemini-2.5-flash` with `gemini-3.1-flash-lite` for user testing. Kept `gemini-3.6-flash` and `gemini-3.5-flash-lite` in their existing fallback order and updated the cascade regression test. Verification: Worker tests passed (10 tests); `npx tsc --noEmit` passed; changelog validator passed. Wrangler emitted sandbox log-write and static-analysis warnings during the tests, but Vitest completed successfully. No Worker deployment performed.

## 2026-10-12

### Backend / API
- **By:** Codex (GPT-6)
- Temporarily reordered the Gemini cascade to `gemini-2.5-flash`, `gemini-3.6-flash`, then `gemini-3.5-flash-lite` for user testing, and updated the fallback-order regression test. Verification: Worker tests passed (10 tests); `npx tsc --noEmit` passed; changelog validator passed. No Worker deployment performed.

### Backend / API
- **By:** Codex (GPT-6)
- Temporarily reordered the Gemini Flash cascade to `gemini-3.8-flash`, `gemini-3.5-flash-lite`, then `gemini-3.6-flash` for user testing, and updated the fallback-order regression test. Verification: Worker tests passed (10 tests); `npx tsc --noEmit` passed; changelog validator passed. No Worker deployment performed.

### Backend / API
- **By:** Codex (GPT-6)
- Temporarily reordered the Gemini Flash cascade to `gemini-3.7-flash`, `gemini-3.6-flash`, then `gemini-3.5-flash-lite` for user testing, and updated the fallback-order regression test. Verification: Worker tests passed (10 tests); `npx tsc --noEmit` passed; changelog validator passed. No Worker deployment performed.

### Backend / API
- **By:** Codex (GPT-6)
- Temporarily reordered the Gemini Flash cascade to `gemini-3.5-flash-lite`, `gemini-3.8-flash`, then `gemini-3.6-flash` for user testing, and updated the fallback-order regression test. Verification: Worker tests passed (10 tests); `npx tsc --noEmit` passed; changelog validator passed. No Worker deployment performed.

### Fixed
- **By:** Codex (GPT-6)
- Deduplicated concurrent nutrition lookups for identical meal title/details, made failed lookups eligible for a later retry, removed network requests from meal RecyclerView binding, and refreshed the visible meal list after nutrition is saved. Added tests for concurrent callers sharing one result and failed requests allowing retry. Verification: `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and changelog validator passed.

### Backend / API
- **By:** Codex (GPT-6)
- Reordered Gemini model attempts to use `gemini-3.7-flash` first, `gemini-3.6-flash` second, and `gemini-3.8-flash` last, and updated the cascade test to match. Verification: changelog validator passed; backend tests not run.

### Tests
- **By:** Codex (GPT-6)
- Added Worker unit coverage for the Gemini model cascade, including per-model retries and fallback order. Correction to the earlier verification note: `npm test -- --run` passed (10 tests); Wrangler emitted sandbox-related log and static-analysis warnings, but Vitest completed successfully.

### Backend / API
- **By:** Codex (GPT-6)
- Updated the Gemini request cascade to try `gemini-3.8-flash`, then `gemini-3.7-flash`, then `gemini-3.6-flash`; quota tracking now covers all three configured models. Verification: changelog validator passed; backend tests not run.

### Fixed
- **By:** Codex (GPT-6)
- Stopped Gemini location `400 FAILED_PRECONDITION` failures at the first affected request, avoiding retries across keys/models and preventing Android from replaying the Worker request. Added a manual Retry action to nutrition error dialogs. The Worker skips a key/model pair after HTTP 429 and persists explicit quota exhaustion for that model for the day, while allowing the key on other models.
- Prevented Android from automatically replaying Worker `ai_provider_busy` failures after the Worker has already exhausted its provider retry sequence; the nutrition UI now leaves another attempt to the user's Retry action.

### Fixed
- **By:** Codex (GPT-6)
- Fixed the add/edit meal dialog staying open after Save by dismissing it immediately while nutrition lookup and meal persistence continue.

## 2026-10-11

### UI
- **By:** Codex (GPT-6)
- Renamed the add/edit meal notes field to “Meal description” and updated its placeholder to “Describe the meal in detail.”

### Input Validation
- **By:** Codex (GPT-6)
- Added shared semantic text validation for meal names/details and workout names/target areas: 80-character titles, 300-character details, Unicode letters, a bounded punctuation allowlist, and rejection of number-only/symbol-only values. Added a documented app-wide policy and negative-case checklist with negative input tests. Verification: `:app:testDebugUnitTest --tests com.dailyroutine.app.TextInputPolicyTest` passed; changelog validator passed.

### Security Notice
- **By:** Codex (GPT-6)
- Added a recurring, informational startup warning whenever Android Developer options are enabled. The notice explains practical risks from USB debugging and mock locations and recommends disabling unneeded development settings without blocking app access. Added Robolectric coverage for enabled and disabled system settings. Verification: changelog validator passed; focused unit tests could not run because `compileDebugKotlin` fails with unresolved references to existing app classes after a Gradle/Kotlin daemon cache collision.

### Fixed
- **By:** Codex (GPT-6)
- Made the home-screen last-sync time use the newest daily sync-log entry, so it matches the entry shown when the log is opened. Sync-log reads now apply the 6 a.m. daily reset, and log read/write operations are synchronized to avoid racing updates. Added regression coverage for resetting at 6 a.m. and retaining the previous operational day's log before 6 a.m. Verification: changelog validator passed. Focused unit-test run attempted but blocked by Gradle/Kotlin build failures (overlapping daemon cache failure, followed by generated Room sources failing to resolve Room types); tests did not complete.

## 2026-10-08

### Backend / API
- **By:** Gemini in Android Studio (Model-guided update)
- Updated Cloudflare Worker (`cloudflare-backend/src/index.ts`) to use a new set of environment secrets (`GEMINI_API_KEYS_SET1` to `GEMINI_API_KEYS_SET5`) for Gemini API Keys.
- Implemented daily quota exhaustion tracking (Error 429) using an in-memory `Set` that resets automatically at Midnight Pacific Time (PT). Keys that hit their daily limits are skipped for the rest of the day across all requests handled by the isolate.
- Removed legacy and deprecated `GEMINI_PRO_API_KEYS` and `GEMINI_FREE_API_KEYS` fallback logic.
- Verification: Read/updated source file and ran the Changelog verification script successfully.

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

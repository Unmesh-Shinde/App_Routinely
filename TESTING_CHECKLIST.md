# 🧪 Heart Points Fix - Testing Checklist

## Pre-Installation

- [ ] Read the fix summary (HEART_POINTS_FIX_COMPLETED.md)
- [ ] Backup current app data
- [ ] Have Google Fit or Samsung Health app with recent heart data

## Installation

- [ ] Connect Android device to computer
- [ ] Enable USB debugging on device
- [ ] Install APK: `app-debug.apk`
  ```bash
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```
- [ ] Grant Health Connect permissions when prompted

## Verification - Part 1: Permission Check

**Expected**: App should request and show Heart Rate permission

- [ ] Open Settings → Apps → Health Connect → Routinely
- [ ] Check "Heart Rate" permission is granted
- [ ] If not granted, manually enable it

## Verification - Part 2: Today's Heart Points

**Expected**: Heart points should show (not 0)

1. [ ] Open Routinely app
2. [ ] Navigate to **Step Tracker** (WalkingDataActivity)
3. [ ] Check **Today** tab
4. [ ] Look for **Heart Pts** card/field
   - [ ] Should show a number > 0 (if you have activity/HR data)
   - [ ] Should match Google Fit approximately
5. [ ] Check Android Logcat for:
   ```
   D/HealthConnectManager: Reading Heart Points from...
   D/HealthConnectManager: Exercise Sessions Heart Points: XX
   or
   D/HealthConnectManager: Heart Rate Heart Points: XX
   D/HealthConnectManager: Total Heart Points: XX
   ```

## Verification - Part 3: Weekly Data

**Expected**: Past days should show heart points

1. [ ] Stay in Step Tracker app
2. [ ] Navigate to **Weekly** tab
3. [ ] Should see orange/red bars (heart points) alongside green bars (steps)
4. [ ] Check multiple days:
   - [ ] Yesterday: Should match Google Fit (10 points in your case)
   - [ ] 3 days ago: Should show value
   - [ ] 7 days ago: Should show value
   - [ ] Not all should be 0 (unless no activity those days)

## Verification - Part 4: Data Comparison

**Expected**: App heart points ≈ Google Fit heart points

1. [ ] Open **Google Fit** app
2. [ ] Go to **Heart Points** section
3. [ ] Note values for:
   - [ ] Today: _____ points
   - [ ] Yesterday: _____ points
   - [ ] Week ago: _____ points

4. [ ] Compare with your app:
   - [ ] Today app vs Google Fit: ✓ Match or close
   - [ ] Yesterday app vs Google Fit: ✓ Match or close
   - [ ] Week ago app vs Google Fit: ✓ Match or close

## Troubleshooting - If Heart Points Show 0

### Check 1: Verify Permissions

```bash
adb shell pm list permissions | grep health
```

- [ ] Should show: `android.permission.health.READ_HEART_RATE`

### Check 2: Verify Health Connect Has Data

1. [ ] Open **Health Connect** app
2. [ ] Check if Google Fit is connected
3. [ ] Browse to **Heart Rate** or **Workouts**
4. [ ] Should see recent data

### Check 3: Check Logcat for Errors

```bash
adb logcat -s "HealthConnectManager:HealthSyncWorker"
```

Look for:
- [ ] `ExerciseSessionRecord read failed:` - Check Exercise permission
- [ ] `HeartRateRecord read failed:` - Check Heart Rate permission
- [ ] `Total Heart Points: 0` - May indicate no HR data in that time range

### Check 4: Force Manual Sync

1. [ ] Open Routinely app
2. [ ] Go to **Step Tracker**
3. [ ] Pull down to refresh (if available)
4. [ ] Wait 5-10 seconds
5. [ ] Check if heart points appear

## Troubleshooting - If Data Doesn't Match Google Fit

### Possible Causes

1. **Time Zone Issue**
   - [ ] Check device time zone matches expected
   - [ ] Google Fit might be showing different time zone

2. **Data Source Difference**
   - [ ] Google Fit pulls from multiple apps (Fitness, Strava, etc.)
   - [ ] Our app pulls from Health Connect
   - [ ] If Google Fit synced from non-connected app, we might not see it

3. **Recent Sync Needed**
   - [ ] In Google Fit app, manually trigger sync
   - [ ] Wait 30 seconds
   - [ ] Open Routinely and check again

### Fix Steps

- [ ] Manually sync Google Fit → Health Connect
- [ ] Restart Routinely app
- [ ] Check Step Tracker again
- [ ] If still not matching, note the discrepancy for debugging

## Expected Results Matrix

| Data Source | Today | Yesterday | 3 Days Ago | Status |
|-------------|-------|-----------|-----------|---------|
| Google Fit | 10 | 10 | 5 | Reference |
| Routinely (Before Fix) | 0 | 0 | 0 | ❌ BROKEN |
| Routinely (After Fix) | ~10 | ~10 | ~5 | ✅ FIXED |

---

## Success Criteria

### Minimum Success
- [ ] App shows heart points (not all 0)
- [ ] Today's value is > 0 if you have activity
- [ ] No crashes or errors in logcat

### Full Success
- [ ] Today's value matches Google Fit ±5%
- [ ] Yesterday's value matches Google Fit ±5%
- [ ] Past days show variety (not all 0)
- [ ] Weekly tab shows bars for multiple days
- [ ] All 3 tabs (Today/Weekly/Monthly) work without crashing

---

## Debug Commands

```bash
# View real-time logs
adb logcat -s "HealthConnectManager" -v threadtime

# Check installed permissions
adb shell pm list permissions | grep health

# Clear app data and restart (if needed)
adb shell pm clear com.dailyroutine.app
adb shell am start -n com.dailyroutine.app/.MainActivity

# Uninstall old APK
adb uninstall com.dailyroutine.app
```

---

## Contact Info for Issues

If you encounter problems:
1. [ ] Note the exact error message
2. [ ] Share logcat output
3. [ ] Compare with Google Fit values
4. [ ] Note your Google Fit data source (Workouts, Heart Rate, etc.)

---

## ✅ Complete - Ready to Test!

The app is now built and ready to test. Follow the checklist above and report back on your findings.

**Key Points to Share**:
- What you see in Today tab
- What Google Fit shows for today
- Any error messages from logcat
- Whether past days are now showing data

Good luck! 🚀

# Heart Points Sync - Quick Testing Guide

## What Was Fixed

✅ **Improved Heart Rate Zone Calculations**
- Now matches Google Fit's algorithm
- 90-110 BPM: 0.5 points/min (fat burn)
- 110-130 BPM: 1.0 point/min (cardio)
- 130+ BPM: 2.0 points/min (peak)

✅ **Independent Heart Points Sync**
- No longer dependent on steps sync flag
- Syncs whenever heart rate permission is granted

✅ **Better Error Handling & Logging**
- Comprehensive debug logging
- Track what's syncing and why failures occur

✅ **Fixed Duration Capping**
- Removed 5-minute max cap that was limiting points
- Exercise sessions now get full credit

---

## How to Verify the Fix

### 1. **Check Logcat (Android Studio)**
```
Search for: "HealthSyncWorker" or "WalkingDataActivity"
```

**Expected outputs:**
```
D/HealthSyncWorker: Heart Points synced for today: 45
D/WalkingDataActivity: Today Heart Points: 45
D/WalkingDataActivity: Weekly - Date: 2026-07-01, Steps: 8234, HP: 45
```

### 2. **Manual Testing in App**

**Step A: Ensure Data Exists**
1. Open Google Fit or Samsung Health
2. Ensure you have heart rate data or exercise sessions recorded
3. Make sure it's linked to Health Connect

**Step B: Test Today Tab**
1. Open Routinely app → Step Tracker
2. Check "Heart Pts" card value
3. Should show a number > 0 if activity exists

**Step C: Test Weekly Tab**
1. Click "Weekly" tab
2. Look for heart-colored bars next to step bars
3. Check values in the text above each bar
4. Should match the heart rate data from your health app

**Step D: Test Monthly Tab**
1. Click "Monthly" tab
2. Scroll through weeks
3. Look for secondary values (heart points)
4. Should correlate with exercise intensity

### 3. **Force Sync & Refresh**
1. Close the app completely
2. Reopen the app
3. Navigate to Step Tracker
4. App auto-syncs on load
5. Heart points should appear within 5-10 seconds

---

## Data Display Locations

| Screen | Component | ID | Expected Value |
|--------|-----------|----|--------------------|
| **Today Tab** | Heart Points Card | `tvHeartPoints` | Today's total |
| **Weekly Tab** | Heart Value Label | `tvHeartValue` | Daily breakdown |
| **Weekly Tab** | Heart Bar | `viewHeartBar` | Visual bar height |
| **Monthly Tab** | Week Summary | Adapter | Weekly aggregate |

---

## Permission Checklist

For heart points to work, these must be true:

- [ ] App has Health Connect installed on device
- [ ] App has "Heart Rate" permission granted
- [ ] Health data source app (Google Fit/Samsung Health) is linked to Health Connect
- [ ] Heart rate data is being recorded in the source app
- [ ] Health Connect data is synced (may need manual sync in HC app)

---

## Troubleshooting Decision Tree

```
Heart Points showing 0?
├─ Check Logcat
│  ├─ Error messages? → Check permissions
│  └─ No logs at all? → App not syncing
├─ Check source app (Google Fit / Samsung Health)
│  ├─ Has heart rate data? → Force sync in source app
│  └─ No data? → Record new activity
├─ Check Health Connect app permissions
│  ├─ Granted? → Proceed to next step
│  └─ Not granted? → Grant HeartRate permission
└─ Try manual refresh
   ├─ Kill app, reopen → Triggers manual sync
   └─ Wait 30+ seconds for sync to complete
```

---

## Key Files Modified

1. **HealthConnectManager.kt**
   - `readHeartPoints()` function (lines 150-210)
   - Improved algorithm, better thresholds, error logging

2. **HealthSyncWorker.kt**
   - Today's sync (lines 74-83) - Independent heart points
   - Historical sync (lines 113-124) - With logging

3. **WalkingDataActivity.kt**
   - Added debug logs for tracking (lines 102, ~126)

---

## Performance Impact

- ⚡ **Negligible** - Same as before, just better logic
- 🔄 Sync runs in background threads (IO dispatcher)
- 📊 Historical data batched (180 days cached locally)
- 💾 Data stored in SharedPreferences (instant access)

---

## Next Steps

After verifying the fix works:

1. **Monitor Logs** - Review logcat regularly to ensure sync is working
2. **Test Different Activities** - Try various workouts to test all zones
3. **Check UI** - Verify bars and values display correctly
4. **Report Issues** - If problems persist, share:
   - Logcat output (especially HealthSyncWorker logs)
   - Screenshots of the app screens
   - Details about health data source app

---

## Success Indicators

✅ You've successfully fixed heart points sync when:
- [ ] Logcat shows "Heart Points synced for today: [number]"
- [ ] Today tab shows heart points value > 0
- [ ] Weekly tab displays heart bars and values
- [ ] Heart points appear after opening app
- [ ] Heart points persist after app restart

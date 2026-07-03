# Google Fit Heart Points OAuth Setup

Heart Points from Google Fit are **not** granted by Android Health Connect permissions.

Health Connect permissions allow the app to read Health Connect data types. Google Fit Heart Points via `play-services-fitness` require a separate Google OAuth authorization tied to the user's Google account and to this app's Android OAuth client.

## Current debug build values

Use these values in Google Cloud Console for the debug APK built on this machine:

```text
Package name: com.dailyroutine.app
SHA-1: F0:BD:00:C7:25:A3:C0:32:73:6E:4E:1C:78:FC:C2:2B:54:A7:A1:E0
SHA-256: 8A:11:A1:DA:88:EA:11:6D:A0:4D:5A:4B:58:27:BB:80:DD:BE:02:FE:39:1E:CA:A4:5C:03:60:1A:04:A1:BD:DC
```

## Required Google Cloud configuration

1. Open Google Cloud Console.
2. Select the project used for this app / Google Sign-In.
3. Enable **Google Fit API**.
4. Configure OAuth consent screen.
5. If the app is in Testing mode, add the Gmail account used in Google Fit as a test user.
6. Create or update an Android OAuth client with:

```text
Package name: com.dailyroutine.app
SHA-1: F0:BD:00:C7:25:A3:C0:32:73:6E:4E:1C:78:FC:C2:2B:54:A7:A1:E0
```

## Meaning of Status 10

`Status 10` from Google Sign-In means `DEVELOPER_ERROR`.

For this app, that means Google rejected the installed APK before granting the Google Fit `fitness.activity.read` scope. The most common causes are:

- The Android OAuth client is missing.
- The OAuth client uses a different package name.
- The OAuth client uses a different SHA-1 certificate.
- Google Fit API is not enabled for the project.
- The OAuth consent screen is in Testing and the selected Gmail account is not added as a test user.

This is why the user may only see Google account selection and never see a third-party connection entry for Routinely.

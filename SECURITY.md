# Security policy

Thank you for helping keep LijnCtrl and its users safe.

## Reporting a problem

**Please don't report security problems in a public issue.** Report them privately instead:

1. Go to the [Security tab](https://github.com/VoidedKN0X/LijnCtrl/security) of this repo.
2. Click **Report a vulnerability** and describe what you found, how to reproduce it, and which version of LijnCtrl you used.

Only the maintainer can see the report. I'll reply as soon as I can, work on a fix, and credit you in the changelog if you'd like.

## Supported versions

Only the latest release gets security fixes. Please update before reporting, and check the [changelog](CHANGELOG.md) to see whether it's already fixed.

## What counts

LijnCtrl has no server of its own. It talks to De Lijn's API, OpenStreetMap's map tiles and, only if you turn it on, Transitous. So these count:

- Your De Lijn API keys, your location, saved stops or planned routes going anywhere other than the services above, or to them over plain HTTP
- Ways around the app lock, such as reading the API key while LijnCtrl is locked, or protected screens showing up in screenshots when they shouldn't
- Another app on the phone being able to make LijnCtrl do something you didn't ask for (for example through its widget, shortcuts or the widget setup screen), or to read its data
- Weaknesses in the app lock PIN
- Anything that lets someone run code or read files they shouldn't

Problems in De Lijn's API, Transitous or OpenStreetMap themselves should go to those services.

## How the app protects you

- Only HTTPS, and only the phone's built-in certificate authorities: plain HTTP and user-installed certificates are refused.
- The main HTTP client can only reach api.delijn.be; Transitous has its own client that can only reach api.transitous.org. Redirects are never followed.
- Your API keys are encrypted with a key in the phone's secure hardware, and also with your app PIN while the app lock is on.
- Nothing is backed up or copied to a new phone, and there's no analytics, crash reporting or tracking of any kind.

## How releases are protected

Every release is built from this repo by GitHub Actions and signed with the same release key. Its SHA-256 certificate fingerprint is:

```
FA:BA:C3:F4:A9:95:15:D9:C2:FF:5C:8B:9E:E5:4E:8D:B2:AD:44:EF:87:78:D0:35:CB:E5:0E:C9:33:32:3E:30
```

The [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) only accepts APKs signed with this key.

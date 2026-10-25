# Changelog

What changed in each LijnCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/LijnCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## v0.1.0 (2026-10-25)

The first release of LijnCtrl: live De Lijn departures for your stops, without De Lijn's own app.

### New

- **Your own De Lijn API key.** On first start LijnCtrl explains how to get a free key from De Lijn's developer portal (data.delijn.be), with a button that opens it. Paste the key and tap **Check and save**: LijnCtrl checks it against both De Lijn APIs it uses (departures and stop search) and tells you exactly which one doesn't accept it if something's wrong, before anything is saved.
- **Stops tab**: the stops you save, each with its next three departures, live. It refreshes every 30 seconds while you're looking at it (and stops refreshing when you leave), and you can pull down to refresh right away. Each stop's menu lets you **Rename** it (for example "Home" or "Work"), **Move up**, **Move down** or **Remove from Stops**.
- **Stop pages**: tap any stop for its next 20 departures, with the lines that call there as coloured badges, today's disruptions and diversions, the stop's town and number (with **Copy stop number**), and whether it's an accessible stop. The star at the top adds it to or removes it from your Stops tab.
- **Live departures**: times that come from the bus or tram's actual position show in colour with a live mark, with how many minutes late or early it is (for example "+3 min late · 14:35"). Other times are marked **Timetable**. Cancelled trips are crossed out and marked **Cancelled**, and buses that have already left are left out.
- **Line badges in De Lijn's own colours**: every departure shows the number that's on the bus or tram (such as T1 or 71), coloured like De Lijn's stop signs. LijnCtrl looks lines up once and keeps them on the phone for a week, so this costs almost no extra data.
- **Disruptions**: problems and diversions at a stop show at the top of its page with the lines involved and how long they last. Messages come in English, Dutch, French or German (**Settings > Departures > Language of disruption messages**, English by default).
- **Search tab**: find any De Lijn stop by name (or the number on its sign) as you type. Tap the star next to a result to save it.
- **Nearby tab**: stops within 750 metres, closest first, with how far away each is. Your location is read once, only when you ask, from Android's own location service (no Google services), and is only sent to De Lijn to find the stops around you. If location is off or no fix comes in, the tab says so and offers a way to fix it.
- **Show times as** minutes to go ("4 min", the default, switching to the clock time beyond an hour) or clock time ("14:32"), under **Settings > Departures**. Times are always Belgian time.
- **You page** (tap the profile picture at the top right): change or remove your API key, open the developer portal, Settings, and About (support, bug reports, source code, licence).
- **App lock** with its own PIN of at least 6 digits and optional fingerprint unlock, the same lock as in the other Ctrl apps (**Settings > Privacy and security > App lock**, off by default). While the app is locked, your API key is encrypted with the PIN and nothing is loaded.
- **Hide in screenshots** and **Hide in recent apps**, two separate switches, both off by default. The API key screen and the lock screens are always kept out of screenshots.
- Light, dark and pure black themes, wallpaper colours, and a layout with a side rail on tablets and unfolded foldables.

### Security and privacy

- LijnCtrl only ever talks to De Lijn's API, over HTTPS. Every other address, plain HTTP and redirects are refused before a request leaves the phone, and only the phone's built-in certificate authorities are trusted.
- Your API key is stored encrypted with a key held in the phone's secure hardware, and also with your PIN while the app lock is on. It's never backed up or moved to another phone.
- No telemetry, no analytics, no Google services.

# LijnCtrl

**Live De Lijn departures for your stops.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/LijnCtrl?label=download)](https://github.com/VoidedKN0X/LijnCtrl/releases/latest) ![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) ![Telemetry-free](https://img.shields.io/badge/telemetry-none-brightgreen) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

LijnCtrl is an Android app for De Lijn buses and trams in Flanders, built on De Lijn's open data with your own free API key.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/LijnCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

## Features

- **Stops tab** with your saved stops and lines; each stop shows its next departures, refreshing live every 30 seconds
- **Live times** from the bus or tram's position, with how late or early it is, topped up with the timetable so quiet stops and lines without live data never look empty; cancelled trips are crossed out
- **Line badges in De Lijn's own colours**, with the number that's on the bus or tram
- **Stop pages** with the next 30 departures, the lines that call there, and today's disruptions and diversions
- **Full timetable** for any stop, today or the next six days, per hour and per line
- **Trip pages**: every stop of a bus or tram run, with live times while it's on the road
- **Line pages**: both directions stop by stop, where the next vehicles are heading, and the line's disruptions
- **Route planner** from your location, a stop or an address to anywhere, leaving now, at a time or arriving by a time; planned by Transitous with every Belgian operator, trains included, and live delays, with each route shown on a map (which can follow your location) and your recent routes one tap away
- **Live buses and trams** on the trip map, the route map and the line map (every vehicle on the line), with their line number and heading (needs De Lijn's free GTFS Realtime key)
- **Go home / Go to work** in one tap, also as launcher shortcuts on the app icon (or on your home screen), a **departures board** of every bus leaving near you, and **line filters** so a stop only shows the lines you take
- **Trip guidance**: follow a planned route with a notification that tells you when to leave, when your bus is coming and when to get off, with live times
- **Alerts**: a notification when your bus is a few minutes from your stop, and (optionally) when De Lijn reports a new disruption on your saved stops or lines
- **Search** for stops by name (in any word order) or the number on their sign, lines by number, every stop in a town, and (if you switch it on) shops, addresses and landmarks with the stops around them
- **Nearby** stops as a list or on an OpenStreetMap map
- **Disruptions on the departure board**: a departure hit by a disruption or diversion shows a warning you can tap open for the full message, and trip pages list the disruptions on their line
- **Disruption messages** in English, Dutch, French or German
- **Rename** saved stops ("Home", "Work") and put them in your own order
- **Minutes to go or clock times**, always in Belgian time
- **App lock** with its own PIN and optional fingerprint unlock; your API key stays encrypted with the PIN while the app is locked
- **Hide in screenshots** and **Hide in recent apps**, two separate switches, both off by default

## Screenshots

Coming soon.

## Requirements

- Android 13 or newer, on a 64-bit ARM phone (nearly every phone from the last several years)
- A free De Lijn API key (see [Getting started](#getting-started))
- **Google-free and de-Googled:** no Google Play services, Firebase or other Google services, and the app never contacts Google. It works the same on de-Googled phones (GrapheneOS, CalyxOS, /e/OS, LineageOS without Google apps). Nearby uses Android's own location service.

## Install

1. Download the latest `.apk` file from the [Releases page](https://github.com/VoidedKN0X/LijnCtrl/releases/latest).
2. Open it on your phone. Android may ask you to allow installing apps from your browser or file manager.
3. Updates install over the old version. Your settings and saved stops are kept.

**Automatic updates:** on your phone, tap one of the buttons below.

- **Obtainium:** install [Obtainium](https://github.com/ImranR98/Obtainium) first. The button adds LijnCtrl and installs every new release for you.
- **F-Droid:** adds the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) to F-Droid, Droid-ify or Neo Store, with every Ctrl app and automatic updates.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/LijnCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

If the Obtainium button doesn't open the app, add `https://github.com/VoidedKN0X/LijnCtrl` in Obtainium by hand.

## Getting started

1. Make a free account on De Lijn's developer portal, [data.delijn.be](https://data.delijn.be/).
2. Under **Products**, subscribe to the free **Open Data** product. One key covers both APIs LijnCtrl uses: departures (Core API) and stop search (Search API).
3. Copy your primary key from your profile on the portal, paste it into LijnCtrl and tap **Check and save**.
4. Find your stops with **Search** or **Nearby** and tap the star to add them to the **Stops** tab.

Your API key, **Settings** and About are behind the profile picture at the top right.

## App lock

Turn it on under the profile picture › **Settings > Privacy and security > App lock**. It's off until you turn it on.

- **Its own app PIN** of at least 6 digits, separate from your phone's screen lock. Very obvious PINs such as 111111 or 123456 are refused.
- **Unlock with fingerprint** (optional) only accepts strong biometrics, such as the fingerprint sensor, checked by the phone's secure hardware. Face unlock that only uses the camera isn't accepted. The PIN is still needed after the phone restarts and at least every 72 hours, and adding a new fingerprint to the phone turns fingerprint unlock off until you turn it on again.
- **Your API key is encrypted with the PIN while LijnCtrl is locked.** The key only exists in memory while the app is unlocked and is wiped the moment it locks. The PIN itself is never stored: it's stretched with 600,000 rounds of PBKDF2 and mixed with a key held in the phone's secure hardware, so a copy of the app's files isn't enough to guess it on a computer.
- **Wrong PINs:** after 5 tries you wait 30 seconds, doubling up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't skip the wait.
- **Lock again:** as soon as you leave the app (the default), after 1, 5 or 15 minutes or 1 hour away, or only when the app restarts. Opening a link, the location settings or the location permission question don't count as leaving, unless you stay away for more than 5 minutes.
- **Change app PIN** asks for your current PIN first, and turning the app lock off asks for the PIN too.
- **Forgot your PIN?** It can't be recovered, because it encrypts your API key. The lock screen offers to remove the key and turn the lock off; you paste your key again and your saved stops stay.
- **With the app lock on**, the recent-apps screen always shows a blank card.

Two more switches in the same section work with or without the app lock, and are both off until you turn them on:

- **Hide in screenshots**: screenshots and screen recordings show a blank screen (and so does the recent-apps view).
- **Hide in recent apps**: the recent-apps view shows a blank card instead of LijnCtrl. Screenshots still work.

The API key screen and the lock and PIN screens are always kept out of screenshots and recordings, whatever these switches say.

## Tips and troubleshooting

- **"De Lijn didn't accept your API key"**: check that your subscription to the Open Data product is active on data.delijn.be, then paste the key again under the profile picture › **Change API key**.
- **Times without the live mark** are the timetable: De Lijn has no live position for that trip right now.
- **Nearby can't find you**: check that location is on, and try outside or near a window. LijnCtrl only reads your location when you open Nearby or tap **Find stops near me**.
- **A stop you know isn't in Search**: try the number on its sign, or search for the town and pick **All stops in …**. On the Nearby tab, a larger distance can be set under **Settings > Nearby**, or use the map.
- **A shop or address isn't a stop name**: switch on **Search addresses and places** in Settings (or tap **Turn on** in Search), then search for it; it shows under **Places and addresses**, with the stops around it.
- **The Plan tab asks to turn on Transitous**: routes are planned by Transitous, which receives the start and end of the routes you plan, so it's only used once you agree (at setup, on the Plan tab or under **Settings > Route planner**).
- **No departures soon**: **Full timetable** on the stop page shows when the next ones are, today or on another day.

## Privacy

**Telemetry-free.** LijnCtrl has no analytics, crash reporting, usage stats or tracking of any kind, and no servers of its own. It only talks to De Lijn's API (api.delijn.be), over HTTPS; every other address is refused. The exceptions are the maps and, if you turn it on, Transitous for route planning and address search, both below. Your API key is stored encrypted on the phone (and with your app PIN while the app lock is on) and is only sent to De Lijn. Live vehicle positions come from De Lijn too (its GTFS Realtime feed on api.delijn.be), only while a map with live vehicles is open and only if you've added that key, which is stored encrypted like the main one. Alerts are worked out on the phone from De Lijn's data; disruption alerts check De Lijn every half hour only while that switch is on. Your saved stops and recent routes stay on the phone (recent routes can be switched off in Settings). Your location is read only when you ask for nearby stops, sent only to De Lijn to look them up, and never kept.

**Route planning and address search** use [Transitous](https://transitous.org) (api.transitous.org), a free, open-source community planner, and only once you turn on **Use Transitous** (at setup, on the Plan tab or in Settings). Then the start, end and time of each route you plan, and the addresses and places you type in Search or in From and To, go to Transitous over its own connection that can't reach anything else. De Lijn's open data can't plan routes or search addresses, which is why. Stops are always searched with De Lijn only. Transitous's data sources are listed at [transitous.org/sources](https://transitous.org/sources/).

**The maps** (Nearby, route and trip maps) load its map tiles from OpenStreetMap (tile.openstreetmap.org), which sees the area you're looking at, the same as any website with a map. Tiles are only loaded while the map is open and are cached on the phone. Everything else still only comes from De Lijn.

LijnCtrl isn't made or endorsed by De Lijn. Departures, stops and disruptions come from [De Lijn's open data](https://data.delijn.be/).

## Build from source

You need [Android Studio](https://developer.android.com/studio), or JDK 17 and the Android SDK.

1. Clone the repo:
   ```bash
   git clone https://github.com/VoidedKN0X/LijnCtrl.git
   cd LijnCtrl
   ```
2. Build the app:
   ```bash
   ./gradlew assembleDebug
   ```
   On Windows, use `gradlew.bat assembleDebug`.
3. The APK is in `app/build/outputs/apk/debug/`. Copy it to your phone and install it.

Or open the folder in Android Studio and press **Run** with your phone connected.

## Support the project

LijnCtrl is free and open source. If it's useful to you, tap **Support LijnCtrl** under **About** on the You page (tap the profile picture) to sponsor development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). Found a problem or have an idea? Tap **Report a bug** in the same section, or [open an issue](https://github.com/VoidedKN0X/LijnCtrl/issues). Starring the repo helps too.

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same dark Ctrl / Signal design.

| App | What it's for |
| --- | --- |
| [AdCtrl](https://github.com/VoidedKN0X/AdCtrl) | Control your AdGuard Home DNS server |
| [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| [ElementCtrl](https://github.com/VoidedKN0X/ElementCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| **LijnCtrl** (this app) | Live De Lijn bus and tram departures |
| [MemoCtrl](https://github.com/VoidedKN0X/MemoCtrl) | Notes on your Memos server |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) | Scan and browse documents on Paperless-ngx |
| [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) | Manage your Proxmox VE server |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |
| [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) | Monitor and manage your TrueNAS server |

## License

LijnCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

The [Space Grotesk](https://github.com/floriankarsten/space-grotesk) and [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) fonts are licensed under the SIL Open Font License 1.1; see `app/src/main/assets/licenses/`. The map uses [osmdroid](https://github.com/osmdroid/osmdroid) (Apache 2.0) and map data © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors.

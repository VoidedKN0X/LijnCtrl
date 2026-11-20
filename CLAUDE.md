# LijnCtrl

## Releasing

- A release is published only when `versionName` in `app/build.gradle.kts` is bumped. Bump `versionCode` by 1 at the same time.
- Tags are `vX.Y.Z` and releases are named "LijnCtrl X.Y.Z". Never add a build number (`-build.N`, "(build N)").
- Use the same version for every change that ships together; README, licence and CI changes don't need a bump.

## Changelog

Every new version needs a detailed entry at the top of `CHANGELOG.md`, under a heading for that version (match the existing headings). CI refuses to release without one, and the entry becomes the release notes on GitHub, in Obtainium and in the CtrlF-Droid repo.

- Write for people using the app, not for developers: what's new, what changed, what was fixed.
- Cover every user-visible change in the version, one bullet each, with enough detail to understand it without opening the app.
- Group with `### New`, `### Changed`, `### Fixed` and `### Security` when there's more than a couple of items.

## De Lijn API

- Base URLs: Core `https://api.delijn.be/DLKernOpenData/api/v1`, Search `https://api.delijn.be/DLZoekOpenData/v1`. The key goes in the `Ocp-Apim-Subscription-Key` header. The app's HTTP client refuses every other host.
- Quirks of the live API (see `data/api/DeLijnApi.kt`): the Search API swaps latitude and longitude; identifiers arrive as numbers or strings; departures only carry the internal `lijnnummer`, so public line numbers and colours come from `/lijnen/lijst/{…}` and `/lijnen/lijst/{…}/lijnkleuren` (field `lijnLijnkleurCodesijst`, sic) via `LineRepository`; disruption lists come under `storingen` or `omleidingen`.
- Times are local Belgian time without an offset (`Europe/Brussels`).
- Upcoming departures come from `DepartureRepository`: the live feed (`/real-time`) merged with the stop's timetable (`/dienstregelingen`), because the live feed alone misses later trips and lines without live data.
- Live vehicle positions: GTFS Realtime v3 at `https://api.delijn.be/gtfs/v3/realtime?position=true` (protobuf, gzipped even without a Content-Encoding header; `json=true` for JSON), a separate subscription with its own key (`AppContainer.gtfsKey`, optional). Its trip ids are GTFS ids (`4330_6_122_1LI4180-16_…`), not the Open Data ones, so `LiveVehicles` matches trips by stop number (`stopId` = haltenummer) and expected time. See `data/live/`.
- Other hosts: OpenStreetMap's tile server, used by the osmdroid maps (Nearby, route and trip maps), and api.transitous.org (`TransitousPlanner`, its own OkHttp client limited to that host), only when the user turns on "Use Transitous" (`AppSettings.useTransitous`, off until accepted at setup, on the Plan tab or in Settings). Route planning is Transitous-only since 0.7.0 (the on-phone planner was removed).
- De Lijn's open data has no place or address search: `/zoek/locaties/{query}` answers 404. Places and addresses come from Transitous's geocoder (`TransitousPlanner.geocode`, `/api/v1/geocode`), under the same `useTransitous` switch.

## Encryption at rest

- `filesDir/saved.json` holds `SavedData` (saved stops and lines, line filters, recent routes, Home/Work, seen disruption ids), sealed with AES-256-GCM (`data/crypto/SealedFile`, marker `LIJNCTRL-REST1`) under the data key (`DataKeyStore`: 32 random bytes in `filesDir/data.key`, wrapped by the Keystore key `lijnctrl_data_at_rest` through `KeystoreWrapper`/`KeystoreAes`; no user authentication, so `DisruptionWorker` can read it in the background). `lines.json` (cache) is sealed with the same key. This is independent of the app lock and of `KeystoreCrypto` (the API keys' key): never touch those for it. It protects files copied off the phone, not a running, unlocked app.
- `data/saved/SealedStore` is the only reader and writer of `saved.json`; `FavouritesRepository`, `PlanHistoryRepository`, `SavedPlacesRepository` and `DisruptionAlerts` go through `AppContainer.saved`. Before encryption these values were seven keys in the settings DataStore (`saved_stops`, `saved_lines`, `line_filters`, `plan_history`, `place_home`, `place_work`, `disruptions_seen`); `SettingsStoreHome` still reads them, and still writes them on a phone with no data key.
- The move is one-way and ordered: write sealed temp file, sync, read back, compare, rename, and only then remove the DataStore keys. Leftover keys are removed at a later start only when they equal the file's contents. Reads accept sealed and ordinary files.
- Never delete or overwrite a file that can't be opened, never replace an existing `data.key`, and never create a Keystore key on the decrypt path (`KeystoreAes.existing`). A `saved.json` that can't be opened turns `SealedStore.unreadable` on: `update` does nothing and `AppRoot` shows the "Can't open your saved stops" screen ("Start empty" renames the file aside). An unreadable `lines.json` is just deleted and fetched again.
- Key unwrap, sealing and decryption run on `Dispatchers.IO`; `AppRoot` holds a blank screen until `saved.ready`.
- `cacheDir/timetables.bin` (`data/departures/TimetableCache`) is a cache of day timetables, kept for 6 hours, **of saved stops only** (so it names no stop that `saved.json` doesn't) and **only ever written sealed** with the data key: with no data key nothing is written (never `get(create = true)` there, never an ordinary file), a file that isn't sealed or can't be opened is deleted, and a stop that's no longer saved is dropped from it. Don't widen it to other stops: that would put a list of stops you looked at on the phone.
- Not encrypted, on purpose: the `bus_alerts` SharedPreferences (alarm path), the settings, osmdroid's tile cache in `cacheDir/osmdroid`, WorkManager's own database.
- `SealedFileTest` and `SealedStoreTest` cover the format, the move and the key rules (`./gradlew :app:testDebugUnitTest`; CI doesn't run unit tests).

## Dependency verification

Every library and plugin download is checked against `gradle/verification-metadata.xml` (SHA-256 only, no PGP). When you add, remove or update a dependency or Gradle plugin, run the manual **Dependency verification metadata** workflow (`gh api -X POST repos/VoidedKN0X/LijnCtrl/actions/workflows/verification-metadata.yml/dispatches -f ref=main`), take the file from its log (gzipped and base64-encoded between the BEGIN/END markers), check the diff (only Maven Central, Google's Maven and the Gradle plugin portal) and commit it in the same push. Keep the `<trusted-artifacts>` entries for `-sources.jar` and `-javadoc.jar`. Never turn verification off or to lenient in CI.

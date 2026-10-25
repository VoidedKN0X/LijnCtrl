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
- The one other host is OpenStreetMap's tile server, used only by the osmdroid map in Nearby (not through the app's OkHttp client).

# Changelog

What changed in each LijnCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/LijnCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## v0.3.2 (2026-10-25)

Address and place search that actually works.

### Fixed

- **Addresses, shops and landmarks can be found again**, in Search and when picking **From** or **To** on the Plan tab, for example your own street and house number or "Vandenborre Roeselare". The place search LijnCtrl used turned out not to exist in De Lijn's open data (it always answered "not found"), so places never showed up. They now come from Transitous's free, open-source address search, which uses OpenStreetMap and is limited to results in Belgium.

### New

- **Search addresses and places**: a new switch under **Settings > Search**. It's off by default, because what you type is then also sent to Transitous (transitous.org). Stops are always searched with De Lijn only, whether it's on or not.
- **One-tap prompt**: while the switch is off, typing in Search or in **From**/**To** shows a short card explaining why addresses aren't found, with a **Turn on** button.
- **Results show what they are**: shops and landmarks, streets and exact addresses each get their own icon, with the street and town underneath. A result that is a De Lijn stop opens that stop's departures straight away (or is used as that stop in the route planner); anything else shows the De Lijn stops around it, closest first.

## v0.3.1 (2026-10-25)

### Fixed

- **Addresses and places show up in Search again** (and when picking **From** or **To** on the Plan tab). De Lijn's place search sends the nearest stop's number along with an address. LijnCtrl took every result with a stop number for a stop and left it out of **Places and addresses**, so addresses, shops and landmarks never appeared, for example your own address or "Vandenborre Roeselare". Now only results De Lijn marks as a stop are treated as stops (with a bus icon, opening that stop straight away); everything else shows as a place with the stops around it.
- **Places are read whatever shape De Lijn sends them in.** A place's name, town and position are now picked up from whichever fields De Lijn fills in, with the position either as two numbers or as text, so a different layout no longer makes places silently disappear.

### Changed

- **When the place search fails, Search says so** (with De Lijn's reason) under **Places and addresses**, instead of quietly showing nothing.

## v0.3.0 (2026-10-25)

A route planner, and search that finds shops, addresses and landmarks the way De Lijn's own app does.

### New

- **Route planner: the new Plan tab.** Choose **From** and **To** (your location, a saved stop, any stop, or an address or place), then **Leave now**, **Leave at** or **Arrive by** a date and time, and tap **Plan route**. Each route shows when you leave and arrive, how long it takes, how many changes, and its walks and rides in order. Tap a route for the details: where to walk, where to get on and off (with the platform, when known), how many stops, and how long each ride takes. Stops, lines and trips in a route open their own pages, so you can check live times.
- **Two planners to choose from**, under **Settings > Route planner**:
  - **On this phone** (the default): plans with De Lijn's own data only, so nothing else learns where you're going. It finds the stops within walking distance of both ends, direct buses and trams and routes with one change, and times them with the timetable.
  - **Transitous** (switch on **Plan with Transitous**): a free, open-source community planner with every Belgian operator (De Lijn, NMBS/SNCB, STIB/MIVB, TEC), trains included, longer routes with more changes, and live delays. The start and end of each route you plan are sent to transitous.org. Its data sources are linked under the switch.
- **Route here**: the new button at the top of stop pages and place pages puts that stop or place in **To** on the Plan tab (with your location as **From** if it was empty) and opens it.
- **Places and addresses in Search**, like in De Lijn's app. Shops, landmarks and addresses now show in their own section ("Vandenborre Roeselare", "Kerkstraat 14 Hasselt"). Tap one to see the De Lijn stops around it, closest first, looking further out (up to 2 km) when few stops are close by.

### Fixed

- **Stop names in any word order.** Searching "korenmarkt gent" found nothing, because De Lijn's search only matches the words in the order of the stop's name ("Gent Korenmarkt"). When the words as typed find nothing, LijnCtrl now searches each word and shows the stops that contain all of them.

### Privacy

- With the default planner, your routes stay between your phone and De Lijn. Transitous is only contacted when you switch it on, and then only with the start, end and time of the routes you plan, from its own connection that can't reach anything else. It identifies itself as LijnCtrl, as Transitous asks.

## v0.2.0 (2026-10-25)

More ways to find a stop, departures that no longer go missing, and the features from De Lijn's own app that were missing: lines, full timetables, trip pages and a map.

### Fixed

- **Stops no longer show "No departures coming up" while buses are timetabled.** De Lijn's live feed only covers the next stretch of time and the trips it's tracking, so quieter stops, and lines without live data (such as Flex and night buses), looked empty. Stop pages and the Stops tab now add the timetable's departures that the live feed doesn't mention. These are marked **Timetable**; live ones keep their coloured time and delay. If the live feed is down but the timetable works, you still get departures.
- **Search finds more stops.** Results now come in pages: **Show more** at the bottom loads the next 40, instead of stopping at the first 40.
- **Nearby finds more stops.** It now asks De Lijn for the full 50 stops it allows (it asked for 30) and looks within 1 km by default instead of 750 m. Only De Lijn's own stops are listed, closest first.

### New

- **Lines.** Search for the number on a bus or tram ("71", "T1") to find its line. A line page shows both directions with every stop in order, a badge at the stops where the next bus or tram is heading and when it's due, how many are on the road, and the line's disruptions and diversions. Tap the line badges on a stop page to open them too.
- **Saved lines.** The star on a line page adds it to the top of your Stops tab as a coloured badge.
- **Trip page.** Tap any departure to see every stop that bus or tram calls at, with times: live predictions while it's on the road (refreshing every 30 seconds), the timetable otherwise. Stops it has passed are dimmed, cancelled stops are crossed out, and the stop you came from is highlighted. Tap a stop to open it, or the line at the top to open the line.
- **Full timetable.** **Full timetable** on a stop page lists every departure for today or any of the next six days, grouped by hour, with a filter per line. Today opens at the current hour, and departures that already left are dimmed. Stops with nothing coming up offer it straight away.
- **Search by stop number.** Type the five- or six-digit number from the stop sign to open that stop directly.
- **Search by town.** Type a town or village to get **All stops in …**, which lists every stop there from A to Z with a filter. This finds stops whose own name doesn't mention the town.
- **Map in Nearby.** The map button on the Nearby tab shows De Lijn stops on an OpenStreetMap map. Zoom in and the stops for the area on screen appear; tap one to open it. Your position shows as a dot (when location is allowed), the location button flies to it, and the map is darkened in dark themes. The map opens where you last looked.
- **Distance for nearby stops** under **Settings > Nearby**: 500 m, 750 m, 1 km (the default), 1.5 km or 2 km.

### Changed

- Search now looks for stops, lines and towns at once, in separate sections, with the hint "Stop, line or town".

### Privacy

- The map's tiles come from OpenStreetMap (tile.openstreetmap.org), which sees the area you're looking at. They're only loaded while the map is open, and the app identifies itself as LijnCtrl as OpenStreetMap asks. Stops and departures still only come from De Lijn, and LijnCtrl's own connection to De Lijn still refuses every other address.

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

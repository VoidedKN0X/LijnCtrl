# Changelog

What changed in each LijnCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/LijnCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## v0.11.0 (2026-10-25)

A new look: Ctrl / Signal, the dark, high-contrast design now shared by every Ctrl app. Everything works as before; only how it looks has changed.

### Changed

- **Dark only, with one accent.** LijnCtrl now always uses a near-black background (#08080A), dark grey cards with a thin border, off-white text and a single lavender accent (#B48CFF) instead of the amber one. The status bar and navigation bar take the same background colour, with light icons.
- **New fonts.** All text is now set in Space Grotesk, and numbers, times, stop numbers and section labels in JetBrains Mono. Both are bundled in the app (no font downloads) and both are free under the SIL Open Font License 1.1; their licences are included in the app. Manrope is no longer used.
- **The next departure stands out.** On the Stops tab each saved stop now shows its stop number as a small label, its name in large type, and the next bus or tram as a big card with the line, where it's going, "LIVE" when De Lijn tracks it, and the minutes to go as a giant countdown, with the clock time and any delay beside it. Your first stop's card is filled in the accent colour; the others use the same layout on a dark card. Later departures are listed underneath as simple rows separated by thin lines.
- **Stop pages** open with the stop number and town as a label and the next departure as the same big countdown card, then every later departure as a plain row.
- **Departure times** in lists are in monospaced numbers: in the accent colour with a small square dot when they're live, white when they come from the timetable. "On time", "+2 min late", "Timetable" and "Cancelled" are now small capital labels under the time.
- **New top of the main tabs.** Stops, Nearby, Plan and Search show the LIJN/CTRL wordmark at the top left and your profile picture at the top right, as a rounded square with a small accent badge; the tab's name is below it in large type. Tapping the picture still opens the You page with settings.
- **Pushed pages** (stop, line, trip, timetable, route, settings and others) have a square back button and their title, with no coloured bar behind it.
- **New tab bar.** The frosted, round "island" is replaced by a floating dark bar with rounded corners. The selected tab is a filled accent block with its icon and name; the other tabs show only their icon.
- **New switches.** Switches are rounded squares: accent with a dark knob when on, grey when off. They still work with TalkBack as switches.
- **Settings and the You page** are grouped into dark cards with thin dividers between rows, under small capital labels. The You page has a close button, the app's icon in an accent tile, and a full-width **Support LijnCtrl** button at the bottom, followed by "GOOGLE-FREE · TELEMETRY-FREE · ARM64".
- **New lock screen.** An accent lock tile, "VKNXWORKS · CTRL", "Locked" in large type, square PIN cells that fill with the accent colour, and a three-column keypad of rounded keys with monospaced digits. The fingerprint button is now a key on the keypad, outlined in the accent colour, next to 0 and the delete key. The lock itself, your PIN and fingerprint unlock work exactly as before.
- **Buttons, fields and chips.** Main buttons are accent-filled rounded rectangles; other buttons are dark with a thin border. Text fields are dark with a thin border that turns accent while you type. Filter chips (days, lines, directions, Leave now / Leave at / Arrive by, Stops / Departures, routes on the map) are rounded squares that fill with the accent colour when selected.
- **Dialogs and sheets** are dark cards with 26dp corners and a thin border.
- **Maps.** The buttons and messages on the Nearby, route, trip and line maps are rounded squares on dark cards instead of round bubbles; the "find me" button is accent-filled. The OpenStreetMap credit is in small monospaced type.
- **Route planner.** From and To use small capital labels, the "Turn on Transitous" box is a dark card with an accent border, and route results show their times in monospaced numbers.
- **Line badges** keep De Lijn's own colours, now with slightly squarer corners and monospaced numbers.
- **Disruptions** are shown on dark cards with a red border instead of a red tint.
- **Icons** use the outlined style throughout (filled stars still mark saved stops and lines).

### Removed

- **Theme and wallpaper colours.** Settings no longer has an Appearance section: the Light, Follow system and Pure black themes and **Use wallpaper colours** are gone, because the Ctrl look is dark only. A theme you picked before is ignored.

## v0.10.0 (2026-10-25)

This release also brings everything in 0.9.0 below, which wasn't published on its own.

Trip guidance: LijnCtrl walks you through a planned route.

### New

- **Trip guidance.** Open a planned route and tap **Start trip guidance**. One ongoing notification then always says what's next ("Next: Bus 71 is coming, in 4 min") and you get an alert, with sound and vibration, at each moment that matters:
  - **Time to leave**, when you need to start walking to your first stop.
  - **Your bus or tram is coming**, about 2 minutes before it arrives at your stop (with the platform for trains, when known).
  - **Get off**, about 2 minutes before your stop, and again when the vehicle is seen close to your stop if you've added a GTFS Realtime key.
  - **You've arrived**, at the end, after which guidance stops by itself.
- **Live, not just planned.** For De Lijn rides, guidance follows the live time of your bus or tram at the stop where you get on and the stop where you get off, so the alerts move with delays. Other rides (trains, other operators) follow the planned times from the route.
- **Works in the background.** Guidance keeps going with the app closed or locked, stops after four hours at most, and can be stopped any time with **Stop** in the notification or **Stop trip guidance** on the route page. LijnCtrl asks for permission to show notifications the first time.

## v0.9.0 (2026-10-25)

Quicker everyday trips: Home and Work, a departures board, and only the lines you take.

### New

- **Go home / Go to work.** The Plan tab has two new buttons under From and To. The first time you tap one, pick the place (any stop, address or place); after that, one tap plans a route from where you are to there, leaving now. Long-press a button to change or remove the place. Home and Work also show at the top of the From/To picker, and they're kept only on this phone.
- **Departures board in Nearby.** The stops sheet in Nearby now has **Stops** and **Departures** at the top. **Departures** lists every bus and tram leaving from the 8 closest stops in one list, soonest first, with the stop each one leaves from and how far away it is. Times are live where De Lijn has them, filled in from the timetable otherwise, and refresh every 30 seconds. Tap one to open its trip.
- **Choose lines per stop.** A stop served by several lines has a new filter button at the top: tick the lines you care about, and only those are shown at that stop, both on its page and on the Stops tab. The stop page says which lines it's showing, with a **Show all** button to undo it. The filter button turns amber while a filter is on.

## v0.8.0 (2026-10-25)

Alerts, and every bus on a line live.

### New

- **"Your bus is almost here" alert.** Open a trip from your stop (tap a departure) and tap the new bell at the top, then choose 2, 5, 10 or 15 minutes. LijnCtrl watches that bus's live time at your stop in a quiet ongoing notification ("8 min (live)"), and when it's that many minutes away it alerts you with sound and vibration. It also tells you if the bus is cancelled or has already left, and stops by itself after the alert, after three hours, or when you tap **Stop** in the notification. It keeps working while the app is closed or locked. LijnCtrl asks for permission to show notifications the first time.
- **Disruption alerts.** A new **Settings > Alerts > Disruption alerts** switch (off by default). Every half hour, when there's a network, LijnCtrl checks your saved stops and lines for disruptions and diversions and sends a notification for each new one, in the language you chose for messages. What's already going on when you turn it on isn't sent, so you only hear about new disruptions. Because it has to read your API key while the app is closed, it only works while the app lock is off (the setting says so when the lock is on).
- **Every bus or tram on a line, live.** Line pages have a new map button: it shows the line's route and stops (tap a stop to open it), and, with a GTFS Realtime key, every vehicle on the road in the chosen direction, moving live with its heading. Switch direction with the chips above the map. The list of vehicles is refreshed every two minutes while the map is open; their positions every few seconds.

## v0.7.0 (2026-10-25)

A simpler, cleaner LijnCtrl: fewer switches, one planner, setup on one screen, and the things you look at most made easier to read.

### Changed

- **One route planner: Transitous.** The planner that ran on the phone with De Lijn's data only (buses and trams, one change at most, timetable times) is gone. Routes are now always planned by Transitous, with every Belgian operator, trains included, more changes and live delays. Because Transitous receives the start and end of the routes you plan, the Plan tab asks once before using it, with a **Turn on Transitous** button.
- **One switch for Transitous.** **Plan with Transitous** and **Search addresses and places** are now a single **Use Transitous** switch under **Settings > Route planner**, covering route planning and finding addresses and places. If you had either switch on, it stays on.
- **Setup on one screen.** First start now asks for your De Lijn key and, on the same screen, whether to use Transitous and (optionally) your GTFS Realtime key for live buses, so everything is ready in one go. Both keys are checked with De Lijn before they're saved.
- **Nearby is one map.** Nearby now opens on the map with the stops near you in a sheet at the bottom: pull it up for the full list, push it down to see more map. The map moves to where you are when it finds you, and the refresh button at the top looks again. There's no more switching between a list and a map.
- **The next bus stands out on your saved stops.** On the Stops tab, the first departure of each saved stop is now shown big: the line badge, where it's going, whether it's on time, late, early or cancelled, and the minutes (or time) to go in large type, in the accent colour when it's live. Later departures stay listed below it, smaller.

### New

- **Route here from search results.** Every stop and place in Search now has a **Route here** button that opens the Plan tab with it as the destination (and your location as the start, if you hadn't picked one), so you don't need to open the stop first.

## v0.6.1 (2026-10-25)

Live buses and trams that keep up and move smoothly.

### Changed

- **Live positions update much faster.** Once your bus or tram is found, LijnCtrl asks De Lijn for just that vehicle every 3 seconds (a tiny request) instead of reloading the whole live feed every 15 seconds, so a new position shows up within a few seconds of the vehicle reporting it. Buses and trams themselves report about every 15 seconds. If De Lijn doesn't allow asking for one vehicle, LijnCtrl notices straight away and falls back to the whole feed every 12 seconds. The whole feed is still checked every 45 seconds, in case the vehicle starts another trip.
- **Buses and trams move smoothly on the map.** Instead of jumping from one report to the next, a vehicle now keeps moving along its route at the speed measured from its last reports, and glides to each new position when it arrives. It stops moving on after 15 seconds without a report, so it never drifts far from where it really is. The pointer turns with the route as the vehicle goes round corners.

## v0.6.0 (2026-10-25)

See where your bus or tram is right now.

### New

- **Live buses and trams on the map.** With a GTFS Realtime key (see below), LijnCtrl shows where the vehicle actually is, from De Lijn's live vehicle feed: a circle in the line's colour with its number, and a pointer showing which way it's heading. Positions update every 15 seconds while the map is open, and a chip at the top says how fresh the position is ("Live position · 12 s ago"), that it's still looking, or that De Lijn has no position for that bus right now.
- **Trip map.** A trip page has a new map button at the top: it shows the whole run with every stop (tap one to open it) and the bus or tram on its way, zooming to it the first time it's found. Tap the button again for the list of stops.
- **Your bus on the route map.** On the route planner's full-screen map, each De Lijn ride that's on its way or leaving within the next hour and a half shows its bus or tram live, so you can see it coming.
- **Live buses and trams in Settings**: a new **GTFS Realtime key** entry. Live positions come from a separate, free De Lijn subscription with its own key: on data.delijn.be, subscribe to **GTFS Realtime** and paste its key there. LijnCtrl checks the key with De Lijn before saving it, and stores it encrypted like your main key (sealed with your PIN while the app lock is on). Without it, the maps show a chip that takes you to that screen.

### Changed

- While a map with live vehicles is open, LijnCtrl downloads De Lijn's live feed (about 250 KB) at most once every 12 to 15 seconds, however many vehicles are shown. Nothing is downloaded for live positions when no such map is open, or without the key.

## v0.5.0 (2026-10-25)

Recent routes in the planner, and a route map that follows you.

### New

- **Recent routes on the Plan tab.** The places you plan between are remembered and listed under **Recent routes** until you plan again, newest first (up to 15). Each one shows where from, where to, and when you last planned it. Tap one to plan that route again straight away, from now; tap **×** to remove one, or **Clear** to remove them all. Planning the same route again moves it back to the top instead of adding it twice.
- **"My location" stays "my location".** A route planned from or to your location is remembered as just that, never as where you were at the time, so planning it again uses where you are then.
- **Remember recent routes**: a new switch under **Settings > Route planner**, on by default. Only from and to are kept, on this phone only. Turning it off forgets every route that was kept and stops remembering new ones.
- **Follow my location on the route map.** The new button in the corner of the full-screen route map centres the map on you and keeps following as you move (it turns amber while it's following). Move the map yourself or tap the button again to stop. If location isn't allowed yet, the button asks for it first.

## v0.4.0 (2026-10-25)

Routes on a map.

### New

- **Route map.** After you plan a route, tap **Map** above the results to see the routes on a full-screen OpenStreetMap map. The chips at the bottom switch between the routes found (shown by their departure and arrival time), and the card under them shows the chosen route and opens its details.
- **Map preview on each route's page.** A small map of the whole route now sits at the top of a route's details; tap it to open the full map.
- **What the map shows:** each bus, tram or train ride as a line in that line's own colour (with a thin edge so yellow lines stay visible), walks as a dotted grey line, the start as a ring and the destination as a filled dot, and a small dot where you get on and off. Tap one of those dots to open that stop's page with its live departures. Your own position shows as well when location is allowed. The map turns dark with the dark theme, like the Nearby map.
- **Real route shapes with Transitous:** when **Plan with Transitous** is on, rides and walks follow the actual streets and tracks. The planner on the phone draws each ride along the stops it passes, and walks as a straight line.

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

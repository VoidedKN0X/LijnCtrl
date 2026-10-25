package com.vknxworks.lijnctrl.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

// De Lijn Open Data responses. Field names are the API's Dutch ones. Nullability follows what the live API
// returns, not its OpenAPI spec: almost everything can be missing, and identifiers arrive as numbers or strings
// (the JSON reader is lenient, so both decode to String).

@Serializable
data class GeoCoordinate(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
)

@Serializable
data class ApiLink(val rel: String? = null, val url: String? = null)

/** A stop as returned by the search API (only a few fields) or by the core API (all of them). */
@Serializable
data class ApiStop(
    @SerialName("entiteitnummer") val entity: String,
    @SerialName("haltenummer") val number: String,
    @SerialName("omschrijving") val name: String? = null,
    @SerialName("omschrijvingLang") val longName: String? = null,
    @SerialName("omschrijvingGemeente") val municipality: String? = null,
    @SerialName("geoCoordinaat") val coordinate: GeoCoordinate? = null,
    @SerialName("halteToegankelijkheden") val accessibility: List<String> = emptyList(),
)

@Serializable
data class StopList(@SerialName("haltes") val stops: List<ApiStop> = emptyList())

@Serializable
data class StopSearchResponse(
    @SerialName("aantalHits") val total: Int = 0,
    @SerialName("haltes") val stops: List<ApiStop> = emptyList(),
)

/** A stop near a coordinate. Its entity number is only in its links. */
@Serializable
data class ApiNearbyStop(
    /** Operator of the stop; De Lijn's own stops are "DELIJN". */
    val type: String? = null,
    val id: String,
    @SerialName("naam") val name: String? = null,
    @SerialName("afstand") val distance: Int? = null,
    @SerialName("geoCoordinaat") val coordinate: GeoCoordinate? = null,
    val links: List<ApiLink> = emptyList(),
)

@Serializable
data class NearbyResponse(@SerialName("haltes") val stops: List<ApiNearbyStop> = emptyList())

/** A bus or tram passing a stop. Times are local (Europe/Brussels) without an offset. */
@Serializable
data class ApiPassage(
    @SerialName("doorkomstId") val id: String? = null,
    /** Only filled in for passages of a trip or line direction (stop-based responses group by stop instead). */
    @SerialName("haltenummer") val stopNumber: String? = null,
    @SerialName("entiteitnummer") val entity: String? = null,
    @SerialName("lijnnummer") val line: String? = null,
    @SerialName("richting") val direction: String? = null,
    @SerialName("ritnummer") val trip: String? = null,
    @SerialName("bestemming") val destination: String? = null,
    @SerialName("plaatsBestemming") val placeDestination: String? = null,
    val vias: List<String> = emptyList(),
    @SerialName("bestemmingKort") val destinationShort: String? = null,
    @SerialName("dienstregelingTijdstip") val scheduled: String? = null,
    @SerialName("real-timeTijdstip") val realtime: String? = null,
    @SerialName("vrtnum") val vehicle: String? = null,
    @SerialName("predictionStatussen") val statuses: List<String> = emptyList(),
)

@Serializable
data class ApiStopPassages(
    @SerialName("haltenummer") val stop: String? = null,
    @SerialName("doorkomsten") val passages: List<ApiPassage> = emptyList(),
)

@Serializable
data class ApiStopSchedule(
    @SerialName("halteDoorkomsten") val stops: List<ApiStopPassages> = emptyList(),
    @SerialName("omleidingen") val diversions: List<ApiDisruption> = emptyList(),
)

@Serializable
data class StopScheduleList(@SerialName("halteDoorkomstenLijst") val schedules: List<ApiStopSchedule> = emptyList())

@Serializable
data class ApiLine(
    @SerialName("entiteitnummer") val entity: String,
    @SerialName("lijnnummer") val line: String,
    @SerialName("lijnnummerPubliek") val publicNumber: String? = null,
    @SerialName("omschrijving") val description: String? = null,
    @SerialName("vervoertype") val transportType: String? = null,
)

@Serializable
data class LineList(@SerialName("lijnen") val lines: List<ApiLine> = emptyList())

@Serializable
data class ColorCode(val code: String? = null)

@Serializable
data class ApiLineColorCodes(
    @SerialName("voorgrond") val foreground: ColorCode? = null,
    @SerialName("achtergrond") val background: ColorCode? = null,
    @SerialName("voorgrondRand") val foregroundBorder: ColorCode? = null,
    @SerialName("achtergrondRand") val backgroundBorder: ColorCode? = null,
)

@Serializable
data class ApiLineRef(
    @SerialName("entiteitnummer") val entity: String,
    @SerialName("lijnnummer") val line: String,
)

@Serializable
data class ApiLineColors(
    @SerialName("lijn") val line: ApiLineRef,
    @SerialName("lijnkleurCodes") val colors: ApiLineColorCodes? = null,
)

/** "lijnLijnkleurCodesijst" (sic) is the name the API really sends. */
@Serializable
data class LineColorList(@SerialName("lijnLijnkleurCodesijst") val lines: List<ApiLineColors> = emptyList())

@Serializable
data class ApiColor(val code: String, val hex: String? = null)

@Serializable
data class ColorList(@SerialName("kleuren") val colors: List<ApiColor> = emptyList())

@Serializable
data class ApiLineDirection(
    @SerialName("entiteitnummer") val entity: String,
    @SerialName("lijnnummer") val line: String,
    @SerialName("richting") val direction: String? = null,
    @SerialName("omschrijving") val description: String? = null,
)

@Serializable
data class LineDirectionList(@SerialName("lijnrichtingen") val directions: List<ApiLineDirection> = emptyList())

@Serializable
data class ApiPeriod(
    @SerialName("startDatum") val start: String? = null,
    @SerialName("eindDatum") val end: String? = null,
)

/**
 * A disruption (storing) or diversion (omleiding); both look the same. Translations are keyed by Dutch
 * language names: nederlands, frans, engels, duits.
 */
@Serializable
data class ApiDisruption(
    @SerialName("titel") val title: String? = null,
    @SerialName("titelMeertalig") val titles: Map<String, String> = emptyMap(),
    @SerialName("omschrijving") val description: String? = null,
    @SerialName("omschrijvingMeertalig") val descriptions: Map<String, String> = emptyMap(),
    @SerialName("periode") val period: ApiPeriod? = null,
    @SerialName("lijnrichtingen") val lineDirections: List<ApiLineDirection> = emptyList(),
    @SerialName("referentieOmleiding") val reference: String? = null,
)

/** The stop and line-direction disruption endpoints use "storingen" or "omleidingen" for the same list. */
@Serializable
data class DisruptionList(
    @SerialName("storingen") val disruptions: List<ApiDisruption> = emptyList(),
    @SerialName("omleidingen") val diversions: List<ApiDisruption> = emptyList(),
) {
    val all: List<ApiDisruption> get() = disruptions + diversions
}

@Serializable
data class ApiEntity(
    @SerialName("entiteitnummer") val entity: String,
    @SerialName("omschrijving") val name: String? = null,
)

@Serializable
data class EntityList(@SerialName("entiteiten") val entities: List<ApiEntity> = emptyList())

// ------------------------------------------------------------- lines, trips and towns (0.2.0)

/** A line direction found by the search API, with the number on the bus and its colours ("#RRGGBB"). */
@Serializable
data class ApiSearchLineDirection(
    @SerialName("entiteitnummer") val entity: String,
    @SerialName("lijnnummer") val line: String,
    @SerialName("lijnNummerPubliek") val publicNumber: String? = null,
    @SerialName("richting") val direction: String? = null,
    @SerialName("omschrijving") val description: String? = null,
    @SerialName("kleurVoorGrond") val foreground: String? = null,
    @SerialName("kleurAchterGrond") val background: String? = null,
    @SerialName("kleurAchterGrondRand") val backgroundBorder: String? = null,
)

@Serializable
data class LineSearchResponse(
    @SerialName("aantalHits") val total: Int = 0,
    @SerialName("lijnrichtingen") val directions: List<ApiSearchLineDirection> = emptyList(),
)

/** One trip (rit) with its passages, as returned for a line direction or a single trip. */
@Serializable
data class ApiTrip(
    @SerialName("ritnummer") val trip: String? = null,
    @SerialName("bestemming") val destination: String? = null,
    @SerialName("plaatsBestemming") val placeDestination: String? = null,
    val vias: List<String> = emptyList(),
    @SerialName("doorkomsten") val passages: List<ApiPassage> = emptyList(),
)

@Serializable
data class ApiLineDirectionSchedule(
    @SerialName("ritDoorkomsten") val trips: List<ApiTrip> = emptyList(),
    @SerialName("omleidingen") val diversions: List<ApiDisruption> = emptyList(),
)

@Serializable
data class ApiMunicipalityRef(
    @SerialName("gemeentenummer") val number: String,
    @SerialName("omschrijving") val name: String? = null,
)

/** A town or village (gemeente). De Lijn uses its own numbers, and sub-municipalities have a main one. */
@Serializable
data class ApiMunicipality(
    @SerialName("gemeentenummer") val number: String,
    @SerialName("omschrijving") val name: String? = null,
    @SerialName("hoofdGemeente") val main: ApiMunicipalityRef? = null,
)

@Serializable
data class MunicipalityList(@SerialName("gemeenten") val municipalities: List<ApiMunicipality> = emptyList())

// ------------------------------------------------------------- places (0.3.0)

/**
 * A place from De Lijn's own geocoder: an address, a point of interest (a shop, a school, a station) or a stop.
 * Every field can be missing; [name] and [street] are the useful ones for showing it.
 */
@Serializable
data class ApiLocation(
    val type: String? = null,
    val subtype: String? = null,
    @SerialName("omschrijving") val name: String? = null,
    @SerialName("naam") val altName: String? = null,
    @SerialName("straat") val street: String? = null,
    @SerialName("huisnummer") val houseNumber: String? = null,
    @SerialName("hoofdgemeente") val town: String? = null,
    /** A list of names, or sometimes a single name. */
    @SerialName("deelgemeentes") val districtsRaw: JsonElement? = null,
    @SerialName("haltenummer") val stopNumber: String? = null,
    @Serializable(with = LenientCoordinateSerializer::class)
    @SerialName("geoCoordinaat") val coordinate: GeoCoordinate? = null,
) {
    val districts: List<String>
        get() = when (val d = districtsRaw) {
            is JsonArray -> d.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            // Sometimes one text with commas: "Putkapel,Leuven".
            is JsonPrimitive -> d.contentOrNull?.split(',').orEmpty()
            else -> emptyList()
        }.map { it.trim() }.filter { it.isNotBlank() }

    /** A stop or station result, which opens that stop instead of the stops around it. */
    val isStop: Boolean
        get() = !stopNumber.isNullOrBlank() && (type.equals("halte", true) || subtype.equals("halte", true))
}

/** The coordinate comes as {"latitude":…,"longitude":…} or, in some responses, as the text "51.03,3.70". */
object LenientCoordinateSerializer : JsonTransformingSerializer<GeoCoordinate>(GeoCoordinate.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        if (element is JsonPrimitive && element.isString) {
            val parts = element.content.split(',').mapNotNull { it.trim().toDoubleOrNull() }
            if (parts.size == 2) return buildJsonObject {
                put("latitude", JsonPrimitive(parts[0]))
                put("longitude", JsonPrimitive(parts[1]))
            }
        }
        return element
    }
}

@Serializable
data class LocationSearchResponse(
    @SerialName("aantalHits") val total: Int = 0,
    @SerialName("locaties") val locations: List<ApiLocation> = emptyList(),
)

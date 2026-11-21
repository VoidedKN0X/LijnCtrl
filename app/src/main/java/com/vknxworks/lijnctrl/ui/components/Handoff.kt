package com.vknxworks.lijnctrl.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.ui.lock.AppLock

/** Handing something to another app: sharing text, and a walk to MapsCtrl. */
object Handoff {
    /** MapsCtrl's package: it opens `geo:` links on its map, with the place ready to route to. */
    const val MAPSCTRL = "com.vknxworks.mapsctrl"

    /** Opens Android's share sheet with [text] (no app is picked for you). */
    fun shareText(context: Context, text: String, title: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        AppLock.expectReturn()
        runCatching { context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /**
     * A `geo:` link for a point with a name, in the form MapsCtrl reads ("geo:lat,lon?q=lat,lon(name)"). Numbers are
     * written with a period whatever the phone's language.
     */
    fun geoUri(latitude: Double, longitude: Double, name: String): Uri {
        val lat = String.format(java.util.Locale.ROOT, "%.6f", latitude)
        val lon = String.format(java.util.Locale.ROOT, "%.6f", longitude)
        return Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(name)})")
    }

    /**
     * Opens the end of a walk in MapsCtrl, where you can start walking directions to it. Without MapsCtrl, any
     * other map app that opens `geo:` links is offered instead; with none, a short message says so.
     */
    fun walkInMapsCtrl(context: Context, latitude: Double, longitude: Double, name: String) {
        val uri = geoUri(latitude, longitude, name)
        AppLock.expectReturn()
        val direct = Intent(Intent.ACTION_VIEW, uri).setPackage(MAPSCTRL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(direct)
            return
        } catch (e: ActivityNotFoundException) {
            // MapsCtrl isn't installed: any map app.
        } catch (e: SecurityException) {
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.no_map_app), Toast.LENGTH_LONG).show()
        }
    }
}

package com.jarvis.breeno.assistant.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/** Safe app-local bridge surface. It exposes compatibility information only. */
class BreenoBridgeProvider : ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun getType(uri: Uri): String? = "application/json"

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val host = BreenoCompatibility.detect(requireNotNull(context))
        return MatrixCursor(arrayOf("installed", "versionCode", "versionName")).apply {
            addRow(arrayOf(host.installed, host.versionCode, host.versionName))
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

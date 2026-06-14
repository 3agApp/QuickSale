package me.sourov.quicksale.ui.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import me.sourov.quicksale.data.update.AppRelease

fun openUpdateUrl(context: Context, release: AppRelease): Boolean =
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.updateUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.isSuccess

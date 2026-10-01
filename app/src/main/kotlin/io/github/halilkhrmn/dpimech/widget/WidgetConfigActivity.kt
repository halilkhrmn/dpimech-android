package io.github.halilkhrmn.dpimech.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.halilkhrmn.dpimech.DpimechApp
import io.github.halilkhrmn.dpimech.R
import io.github.halilkhrmn.dpimech.core.SavedProfiles
import io.github.halilkhrmn.dpimech.ui.DpimechTheme
import io.github.halilkhrmn.dpimech.ui.profileScope

/**
 * Shown when a widget is added (or reconfigured from the launcher): bind it to one profile, or
 * let it follow the selected profile.
 */
class WidgetConfigActivity : AppCompatActivity() {
    private val widgetId get() = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        ?: AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Backing out leaves the widget unplaced (or unchanged when reconfiguring).
        setResult(RESULT_CANCELED, result())
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        enableEdgeToEdge()
        val app = application as DpimechApp
        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle()
            val saved by app.profiles.saved.collectAsStateWithLifecycle()
            DpimechTheme(dynamicColor = settings.dynamicColor) {
                WidgetConfig(saved, WidgetPrefs.boundProfile(this, widgetId), ::pick)
            }
        }
    }

    private fun pick(profileId: String?) {
        WidgetPrefs.bind(this, widgetId, profileId)
        BypassWidget.render(this)
        setResult(RESULT_OK, result())
        finish()
    }

    private fun result() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfig(saved: SavedProfiles, bound: String?, onPick: (String?) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.widget_config_title)) }) }) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Default.SwapHoriz, null) },
                    headlineContent = { Text(stringResource(R.string.widget_config_follow)) },
                    supportingContent = { Text(stringResource(R.string.widget_config_follow_hint)) },
                    trailingContent = { RadioButton(bound == null, onClick = { onPick(null) }) },
                    modifier = Modifier.clickable { onPick(null) },
                )
            }
            items(saved.profiles, key = { it.id }) { p ->
                ListItem(
                    leadingContent = { Icon(Icons.Default.Tune, null) },
                    headlineContent = { Text(p.name) },
                    supportingContent = { Text(profileScope(p)) },
                    trailingContent = { RadioButton(bound == p.id, onClick = { onPick(p.id) }) },
                    modifier = Modifier.clickable { onPick(p.id) },
                )
            }
        }
    }
}

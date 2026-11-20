package com.aeroplay.app.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aeroplay.app.R

/** Compact, transparent app-bar branding; the supplied white wordmark suits dark surfaces. */
@Composable
fun AeroPlayWordmark(modifier: Modifier = Modifier) {
    if (LocalGlassLight.current) {
        // Native lettering keeps the white artwork readable on light backgrounds, without a badge.
        Row(modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AeroPlayAppIcon(Modifier.size(32.dp))
            Row {
                Text("Aero", color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic)
                Text("Play", color = MaterialTheme.colorScheme.primary,
                    fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic)
            }
        }
    } else {
        Image(painterResource(R.drawable.brand_wordmark), contentDescription = "AeroPlay",
            contentScale = ContentScale.Fit, modifier = modifier.size(width = 144.dp, height = 48.dp))
    }
}

@Composable
fun AeroPlayAppIcon(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.brand_app_icon), contentDescription = null,
        contentScale = ContentScale.Fit, modifier = modifier.clip(RoundedCornerShape(12.dp)))
}

@Composable
fun BrandPageHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        AeroPlayAppIcon(Modifier.size(40.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

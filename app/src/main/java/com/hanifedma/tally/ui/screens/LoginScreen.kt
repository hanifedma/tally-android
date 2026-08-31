package com.hanifedma.tally.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanifedma.tally.R
import com.hanifedma.tally.data.Supabase
import com.hanifedma.tally.ui.Fmt
import com.hanifedma.tally.ui.TallyViewModel
import com.hanifedma.tally.ui.theme.LocalTallyColors

/**
 * The way in.
 *
 * One button, and three sentences about what the app is for. The three
 * feature lines are the same ones the web app shows, because someone who has
 * seen one should recognise the other.
 */
@Composable
fun LoginScreen(vm: TallyViewModel, fmt: Fmt, signingIn: Boolean, dark: Boolean) {
    val c = LocalTallyColors.current
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Image(
            painter = painterResource(R.drawable.ic_tally_mark),
            contentDescription = null,
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(21.dp)),
        )
        Spacer(Modifier.height(22.dp))
        Text(
            fmt.t("login.h1"),
            style = MaterialTheme.typography.headlineSmall,
            color = c.text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            fmt.t("login.sub"),
            style = MaterialTheme.typography.bodyLarge,
            color = c.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 340.dp),
        )
        Spacer(Modifier.height(30.dp))

        if (!Supabase.hasGoogleClientId) {
            // Say which piece is missing rather than offering a button that
            // could not work.
            Text(
                fmt.t("err.auth.config"),
                style = MaterialTheme.typography.bodyMedium,
                color = c.warn,
                textAlign = TextAlign.Center,
            )
        } else {
            GoogleButton(
                label = fmt.t("login.google"),
                busy = signingIn,
                onClick = { vm.signIn(context) },
            )
        }

        // Signing in is the better way to use Tally, not the only one. A
        // phone with no Play Services cannot reach the button above at all,
        // and this is the way in that always works.
        Spacer(Modifier.height(20.dp))
        OrRule(fmt.t("login.or"))
        Spacer(Modifier.height(14.dp))
        GhostButton(fmt.t("login.local")) { vm.useLocal() }
        Spacer(Modifier.height(8.dp))
        Text(
            fmt.t("login.localSub"),
            style = MaterialTheme.typography.labelMedium,
            color = c.faint,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 340.dp),
        )

        Spacer(Modifier.height(34.dp))
        Feature("⚡", fmt.t("login.f1"), fmt.t("login.f1sub"))
        Feature("💱", fmt.t("login.f2"), fmt.t("login.f2sub"))
        Feature("🗓", fmt.t("login.f3"), fmt.t("login.f3sub"))

        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallChip(if (dark) "☀️" else "🌙") { vm.toggleTheme() }
            SmallChip(if (fmt.lang == "ko") "EN" else "한국어") { vm.toggleLang() }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            fmt.t("login.privacy"),
            style = MaterialTheme.typography.labelMedium,
            color = c.faint,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun GoogleButton(label: String, busy: Boolean, onClick: () -> Unit) {
    val c = LocalTallyColors.current
    Row(
        Modifier
            .widthIn(min = 240.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(c.surface2)
            .border(1.dp, c.borderStrong, RoundedCornerShape(999.dp))
            .clickable(enabled = !busy, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(
                Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = c.muted,
            )
        } else {
            GoogleMark()
        }
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = c.text,
        )
    }
}

/** Google's four-colour G, drawn rather than shipped as an asset. */
@Composable
private fun GoogleMark() {
    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val stroke = w * 0.22f
        val inset = stroke / 2
        val rect = androidx.compose.ui.geometry.Rect(
            inset, inset, w - inset, size.height - inset,
        )
        val arcs = listOf(
            Triple(-40f, 76f, androidx.compose.ui.graphics.Color(0xFF4285F4)),
            Triple(36f, 96f, androidx.compose.ui.graphics.Color(0xFF34A853)),
            Triple(132f, 92f, androidx.compose.ui.graphics.Color(0xFFFBBC05)),
            Triple(224f, 92f, androidx.compose.ui.graphics.Color(0xFFEA4335)),
        )
        for ((start, sweep, colour) in arcs) {
            drawArc(
                color = colour,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(rect.left, rect.top),
                size = androidx.compose.ui.geometry.Size(rect.width, rect.height),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
            )
        }
        // The bar of the G.
        drawLine(
            color = androidx.compose.ui.graphics.Color(0xFF4285F4),
            start = androidx.compose.ui.geometry.Offset(w * 0.52f, size.height * 0.5f),
            end = androidx.compose.ui.geometry.Offset(w * 0.94f, size.height * 0.5f),
            strokeWidth = stroke,
        )
    }
}

/** A rule with the word centred in the gap. */
@Composable
private fun OrRule(label: String) {
    val c = LocalTallyColors.current
    Row(
        Modifier.fillMaxWidth().widthIn(max = 340.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(c.border))
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.faint)
        Box(Modifier.weight(1f).height(1.dp).background(c.border))
    }
}

@Composable
private fun GhostButton(label: String, onClick: () -> Unit) {
    val c = LocalTallyColors.current
    Box(
        Modifier
            .widthIn(min = 240.dp)
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, c.border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = c.text,
        )
    }
}

@Composable
private fun Feature(glyph: String, title: String, body: String) {
    val c = LocalTallyColors.current
    Row(
        Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(glyph, fontSize = 18.sp, modifier = Modifier.width(24.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = c.text,
            )
            Text(body, style = MaterialTheme.typography.bodyMedium, color = c.muted)
        }
    }
}

@Composable
private fun SmallChip(label: String, onClick: () -> Unit) {
    val c = LocalTallyColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.text)
    }
}

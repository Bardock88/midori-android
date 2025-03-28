package org.midorinext.android.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.midorinext.android.R
import org.midorinext.android.compose.annotation.LightDarkPreview
import org.midorinext.android.theme.MidoriTheme

/**
 * A handle present on top of a bottom sheet. This is selectable when talkback is enabled.
 *
 * @param onRequestDismiss Invoked on clicking the handle when talkback is enabled.
 * @param contentDescription Content Description of the composable.
 * @param modifier The modifier to be applied to the Composable.
 * @param color Color of the handle.
 */
@Composable
fun BottomSheetHandle(
    onRequestDismiss: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    color: Color = MidoriTheme.colors.textSecondary,
) {
    Canvas(
        modifier = modifier
            .height(dimensionResource(id = R.dimen.bottom_sheet_handle_height))
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                onClick {
                    onRequestDismiss()
                    true
                }
            },
    ) {
        drawRect(color = color)
    }
}

@Composable
@LightDarkPreview
private fun BottomSheetHandlePreview() {
    MidoriTheme {
        Column(
            modifier = Modifier
                .background(color = MidoriTheme.colors.layer1)
                .padding(16.dp),
        ) {
            BottomSheetHandle(
                onRequestDismiss = {},
                contentDescription = "",
                modifier = Modifier
                    .width(100.dp)
                    .align(Alignment.CenterHorizontally),
            )
        }
    }
}

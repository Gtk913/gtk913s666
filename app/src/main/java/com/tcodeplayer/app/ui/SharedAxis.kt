package com.tcodeplayer.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Material 3 共享轴（X 轴）页面切换过渡，供各页面统一调用。 */
@Composable
fun <T> SharedAxisTransition(
    target: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = target,
        transitionSpec = {
            (slideInHorizontally { fullWidth -> fullWidth } + fadeIn()) togetherWith
                (slideOutHorizontally { fullWidth -> -fullWidth } + fadeOut())
        },
        label = "sharedAxis",
        modifier = modifier,
    ) { state ->
        content(state)
    }
}

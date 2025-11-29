package io.github.mpichler94.browser

import io.github.mpichler94.browser.render.Drawable

data class CommitData(
    val url: String,
    val scroll: Float,
    val height: Float,
    val displayList: List<Drawable>,
)

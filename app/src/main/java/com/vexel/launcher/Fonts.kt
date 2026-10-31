package com.vexel.launcher

import android.content.Context
import android.graphics.Typeface

/** Inter (SIL Open Font License, see licenses/Inter-OFL.txt), the open-source face closest to Apple's San Francisco. */
object Fonts {
    private var medium: Typeface? = null
    private var regular: Typeface? = null
    private var bold: Typeface? = null

    /** Weight 500: the big clocks. */
    fun medium(context: Context): Typeface =
        medium ?: context.resources.getFont(R.font.inter_medium).also { medium = it }

    /** Weight 400: dates, labels and body text. */
    fun regular(context: Context): Typeface =
        regular ?: context.resources.getFont(R.font.inter_regular).also { regular = it }

    /** Weight 700: titles and chips. */
    fun bold(context: Context): Typeface =
        bold ?: context.resources.getFont(R.font.inter_bold).also { bold = it }
}

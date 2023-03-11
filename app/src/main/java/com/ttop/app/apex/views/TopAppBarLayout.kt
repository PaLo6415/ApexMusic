package com.ttop.app.apex.views

import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.appcompat.widget.Toolbar
import androidx.core.view.updateLayoutParams
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL
import com.google.android.material.shape.MaterialShapeDrawable
import com.ttop.app.apex.databinding.CollapsingAppbarLayoutBinding
import com.ttop.app.apex.databinding.SimpleAppbarLayoutBinding
import com.ttop.app.apex.extensions.accentColor
import com.ttop.app.apex.util.PreferenceUtil
import dev.chrisbanes.insetter.applyInsetter

class TopAppBarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = -1,
) : AppBarLayout(context, attrs, defStyleAttr) {
    private var simpleAppbarBinding: SimpleAppbarLayoutBinding? = null
    private var collapsingAppbarBinding: CollapsingAppbarLayoutBinding? = null

    val mode: AppBarMode = PreferenceUtil.appBarMode

    init {
        if (mode == AppBarMode.COLLAPSING) {
            collapsingAppbarBinding =
                CollapsingAppbarLayoutBinding.inflate(LayoutInflater.from(context), this, true)
            val isLandscape =
                context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            if (isLandscape) {
                fitsSystemWindows = false
            }

        } else {
            simpleAppbarBinding =
                SimpleAppbarLayoutBinding.inflate(LayoutInflater.from(context), this, true)
            simpleAppbarBinding?.root?.applyInsetter {
                type(navigationBars = true) {
                    padding(horizontal = true)
                }
            }
            statusBarForeground = MaterialShapeDrawable.createWithElevationOverlay(context)
        }
    }

    fun pinWhenScrolled() {
        simpleAppbarBinding?.root?.updateLayoutParams<LayoutParams> {
            scrollFlags = SCROLL_FLAG_NO_SCROLL
        }
    }

    val toolbar: Toolbar
        get() = if (mode == AppBarMode.COLLAPSING) {
            collapsingAppbarBinding?.toolbar!!
        } else {
            simpleAppbarBinding?.toolbar!!
        }

    var title: String
        get() = if (mode == AppBarMode.COLLAPSING) {
            collapsingAppbarBinding?.collapsingToolbarLayout?.title.toString()
        } else {
            simpleAppbarBinding?.appNameText?.text.toString()
        }
        set(value) {
            if (mode == AppBarMode.COLLAPSING) {
                collapsingAppbarBinding?.collapsingToolbarLayout?.title = value
                if (PreferenceUtil.isExtendedAccent) {
                    collapsingAppbarBinding?.collapsingToolbarLayout?.setCollapsedTitleTextColor(
                        context.accentColor()
                    )
                    collapsingAppbarBinding?.collapsingToolbarLayout?.setExpandedTitleColor(context.accentColor())
                }
                collapsingAppbarBinding?.collapsingToolbarLayout?.setCollapsedTitleTypeface(Typeface.DEFAULT_BOLD)
                collapsingAppbarBinding?.collapsingToolbarLayout?.setExpandedTitleTypeface(Typeface.DEFAULT_BOLD)
            } else {
                simpleAppbarBinding?.appNameText?.text = value
                if (PreferenceUtil.isExtendedAccent) {
                    simpleAppbarBinding?.appNameText?.setTextColor(context.accentColor())
                }
                simpleAppbarBinding?.appNameText?.setTypeface(Typeface.DEFAULT_BOLD)
            }
        }

    enum class AppBarMode {
        COLLAPSING,
        SIMPLE
    }
}

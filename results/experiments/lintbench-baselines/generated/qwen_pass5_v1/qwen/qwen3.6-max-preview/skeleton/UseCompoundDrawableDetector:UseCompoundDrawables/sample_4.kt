package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.LintUtils
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class UseCompoundDrawableDetector : LayoutDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            UseCompoundDrawableDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a `TextView` with compound drawables",
            explanation = """
                A `LinearLayout` which contains an `ImageView` and a `TextView` can be more \
                efficiently handled as a compound drawable (a single TextView, using the \
                `drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` \
                attributes to draw one or more images adjacent to the text).

                If the two widgets are offset from each other with margins, this can be \
                replaced with a `drawablePadding` attribute.

                There's a lint quickfix to perform this conversion in the Eclipse plugin.
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("LinearLayout")

    override fun visitElement(context: XmlContext, element: Element) {
        val children = LintUtils.getChildren(element)
        if (children.size != 2) return

        var imageView: Element? = null
        var textView: Element? = null

        for (child in children) {
            when (child.tagName) {
                "ImageView" -> imageView = child
                "TextView" -> textView = child
                else -> return
            }
        }

        if (imageView == null || textView == null) return

        // Weights prevent straightforward compound drawable replacement
        if (imageView.hasAttribute("android:layout_weight") ||
            textView.hasAttribute("android:layout_weight")) {
            return
        }

        // ImageView must be wrap_content to fit naturally as a compound drawable
        val imgWidth = imageView.getAttribute("android:layout_width")
        val imgHeight = imageView.getAttribute("android:layout_height")
        if (imgWidth != "wrap_content" || imgHeight != "wrap_content") return

        // TextView dimensions must be compatible
        val txtWidth = textView.getAttribute("android:layout_width")
        val txtHeight = textView.getAttribute("android:layout_height")
        val validWidth = txtWidth == "wrap_content" || txtWidth == "match_parent" || txtWidth == "fill_parent"
        val validHeight = txtHeight == "wrap_content" || txtHeight == "match_parent" || txtHeight == "fill_parent"
        if (!validWidth || !validHeight) return

        // All basic structural conditions met; report the issue
        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "This tag and its children can be replaced by one <TextView/> and a compound drawable"
        )
    }
}
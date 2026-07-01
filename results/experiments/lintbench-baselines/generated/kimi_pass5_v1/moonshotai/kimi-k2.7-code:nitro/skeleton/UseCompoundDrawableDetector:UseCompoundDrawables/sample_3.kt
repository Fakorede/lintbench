package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

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
                A `LinearLayout` which contains an `ImageView` and a `TextView` can be more
                efficiently handled as a compound drawable (a single `TextView`, using the
                `drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom`
                attributes to draw one or more images adjacent to the text).

                If the two widgets are offset from each other with margins, this can be
                replaced with a `drawablePadding` attribute.
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("LinearLayout")

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val children = element.childNodes ?: return
        var elementCount = 0
        var hasImageView = false
        var hasTextView = false

        for (i in 0 until children.length) {
            val child = children.item(i) ?: continue
            if (child is org.w3c.dom.Element) {
                elementCount++
                when (child.tagName) {
                    "ImageView" -> hasImageView = true
                    "TextView" -> hasTextView = true
                    else -> return
                }
            }
        }

        if (elementCount == 2 && hasImageView && hasTextView) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "This tag and its children can be replaced by a single `TextView` with compound drawables",
            )
        }
    }
}
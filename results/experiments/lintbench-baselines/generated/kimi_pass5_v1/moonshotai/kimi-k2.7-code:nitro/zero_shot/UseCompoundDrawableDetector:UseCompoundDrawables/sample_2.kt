package com.android.tools.lint.checks

import com.android.SdkConstants.TAG_IMAGE_VIEW
import com.android.SdkConstants.TAG_LINEAR_LAYOUT
import com.android.SdkConstants.TAG_TEXT_VIEW
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class UseCompoundDrawableDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> = listOf(TAG_LINEAR_LAYOUT)

    override fun visitElement(context: XmlContext, element: Element) {
        val children = (0 until element.childNodes.length)
            .map { element.childNodes.item(it) }
            .filterIsInstance<Element>()

        if (children.size != 2) {
            return
        }

        val hasImageView = children.any { it.tagName == TAG_IMAGE_VIEW }
        val hasTextView = children.any { it.tagName == TAG_TEXT_VIEW }

        if (hasImageView && hasTextView) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "Can be replaced with a compound drawable"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a TextView with compound drawables",
            explanation = """
                A `LinearLayout` which contains an `ImageView` and a `TextView` can be more
                efficiently handled as a compound drawable (a single TextView, using the
                `drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom`
                attributes to draw one or more images adjacent to the text).

                If the two widgets are offset from each other with margins, this can be
                replaced with a `drawablePadding` attribute.
            """,
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                UseCompoundDrawableDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
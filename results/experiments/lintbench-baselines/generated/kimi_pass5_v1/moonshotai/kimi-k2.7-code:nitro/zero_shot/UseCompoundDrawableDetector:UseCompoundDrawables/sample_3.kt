package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SdkConstants
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.EnumSet

class UseCompoundDrawableDetector : Detector(), Detector.XmlScanner {

    override fun getApplicableElements(): Collection<String> =
        listOf(SdkConstants.LINEAR_LAYOUT)

    override fun visitElement(context: XmlContext, element: Element) {
        if (SdkConstants.LINEAR_LAYOUT != element.tagName) {
            return
        }

        var imageView: Element? = null
        var textView: Element? = null
        val childElements = mutableListOf<Element>()

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                childElements.add(childElement)
                when (childElement.tagName) {
                    SdkConstants.IMAGE_VIEW -> imageView = childElement
                    SdkConstants.TEXT_VIEW -> textView = childElement
                    else -> return
                }
            }
        }

        if (imageView == null || textView == null) {
            return
        }

        if (COMPOUND_DRAWABLE_ATTRIBUTES.any { textView.hasAttribute(it) }) {
            return
        }

        val orientation = getLayoutOrientation(element)
        val imageIndex = childElements.indexOf(imageView)
        val textIndex = childElements.indexOf(textView)
        val imageFirst = imageIndex < textIndex

        val suggestedAttribute = when {
            orientation == Orientation.VERTICAL && imageFirst -> SdkConstants.ATTR_DRAWABLE_TOP
            orientation == Orientation.VERTICAL -> SdkConstants.ATTR_DRAWABLE_BOTTOM
            orientation == Orientation.HORIZONTAL && imageFirst -> SdkConstants.ATTR_DRAWABLE_LEFT
            else -> SdkConstants.ATTR_DRAWABLE_RIGHT
        }

        val hasMargin = hasAnyMargin(imageView) || hasAnyMargin(textView)
        val message = buildString {
            append(
                "This LinearLayout with an ImageView and a TextView can be replaced by a single TextView using a compound drawable (`$suggestedAttribute`)"
            )
            if (hasMargin) {
                append("; use `drawablePadding` to replace the spacing between the drawable and the text")
            }
        }

        val fix = if (hasMargin) {
            LintFix.create()
                .name("Use compound drawable with drawablePadding")
                .replace()
                .all()
                .build()
        } else {
            LintFix.create()
                .name("Use compound drawable")
                .replace()
                .all()
                .build()
        }

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            message,
            fix
        )
    }

    private fun getLayoutOrientation(element: Element): Orientation {
        val value = element.getAttribute(SdkConstants.ATTR_ORIENTATION)
            .takeIf { it.isNotBlank() }
            ?: element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ORIENTATION)
                .takeIf { it.isNotBlank() }
            ?: ""
        return if (value == SdkConstants.VALUE_VERTICAL) Orientation.VERTICAL else Orientation.HORIZONTAL
    }

    private fun hasAnyMargin(element: Element): Boolean {
        val attributes = element.attributes ?: return false
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val name = attr.localName ?: attr.name ?: continue
            if (name.startsWith(SdkConstants.ATTR_LAYOUT_MARGIN)) {
                return true
            }
        }
        return false
    }

    private enum class Orientation { HORIZONTAL, VERTICAL }

    companion object {
        private val COMPOUND_DRAWABLE_ATTRIBUTES = listOf(
            SdkConstants.ATTR_DRAWABLE_LEFT,
            SdkConstants.ATTR_DRAWABLE_RIGHT,
            SdkConstants.ATTR_DRAWABLE_TOP,
            SdkConstants.ATTR_DRAWABLE_BOTTOM,
            SdkConstants.ATTR_DRAWABLE_START,
            SdkConstants.ATTR_DRAWABLE_END
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a `TextView` with compound drawables",
            explanation = """
                A `LinearLayout` which contains an `ImageView` and a `TextView` can be more
                efficiently handled as a compound drawable (a single TextView, using the
                `drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom`
                attributes to draw one or more images adjacent to the text).

                If the two widgets are offset from each other with margins, this can be
                replaced with a `drawablePadding` attribute.
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                UseCompoundDrawableDetector::class.java,
                EnumSet.of(Scope.RESOURCE_FILE_SCOPE)
            )
        )
    }
}
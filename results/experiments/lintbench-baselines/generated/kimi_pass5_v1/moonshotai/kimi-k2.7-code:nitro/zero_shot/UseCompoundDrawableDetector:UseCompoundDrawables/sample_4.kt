package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class UseCompoundDrawableDetector : ResourceXmlDetector() {

    override fun getApplicableElements(): Collection<String> =
        listOf(SdkConstants.LINEAR_LAYOUT)

    override fun visitElement(context: XmlContext, element: Element) {
        var imageView: Element? = null
        var textView: Element? = null

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            when (child.tagName) {
                SdkConstants.IMAGE_VIEW -> imageView = child
                SdkConstants.TEXT_VIEW -> textView = child
                else -> return
            }
        }

        if (imageView == null || textView == null) {
            return
        }

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "This layout can be replaced with a TextView using compound drawables"
        )
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a TextView with compound drawables",
            explanation = """
                A LinearLayout which contains an ImageView and a TextView can be more efficiently
                handled as a compound drawable (a single TextView, using the drawableTop, drawableLeft,
                drawableRight and/or drawableBottom attributes to draw one or more images adjacent to
                the text).

                If the two widgets are offset from each other with margins, this can be replaced with a
                drawablePadding attribute.
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                UseCompoundDrawableDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
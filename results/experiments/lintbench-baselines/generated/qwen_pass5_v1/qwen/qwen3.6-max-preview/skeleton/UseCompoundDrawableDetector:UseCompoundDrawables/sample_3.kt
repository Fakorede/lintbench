package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

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
            explanation = "A `LinearLayout` which contains an `ImageView` and a `TextView` can be more efficiently handled as a compound drawable (a single TextView, using the `drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` attributes to draw one or more images adjacent to the text).\n\nIf the two widgets are offset from each other with margins, this can be replaced with a `drawablePadding` attribute.\n\nThere's a lint quickfix to perform this conversion in the Eclipse plugin.",
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("LinearLayout")

    override fun visitElement(context: XmlContext, element: Element) {
        var imageView: Element? = null
        var textView: Element? = null
        var childCount = 0

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                childCount++
                val tag = child.nodeName
                when (tag) {
                    "ImageView" -> imageView = child as Element
                    "TextView", "Button", "EditText", "CheckBox", "RadioButton", "Switch", "ToggleButton" -> textView = child as Element
                }
            }
        }

        if (childCount == 2 && imageView != null && textView != null) {
            val imageWeight = imageView.getAttribute("android:layout_weight")
            val textWeight = textView.getAttribute("android:layout_weight")
            if (imageWeight.isEmpty() && textWeight.isEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "This tag and its children can be replaced by one <TextView/> and a compound drawable"
                )
            }
        }
    }
}
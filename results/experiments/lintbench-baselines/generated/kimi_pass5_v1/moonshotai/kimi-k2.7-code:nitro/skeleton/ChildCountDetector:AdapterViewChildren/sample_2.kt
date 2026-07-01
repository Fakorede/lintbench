package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class ChildCountDetector : LayoutDetector() {

    companion object {
        private const val ADAPTER_VIEW = "android.widget.AdapterView"

        private val IMPLEMENTATION = Implementation(
            ChildCountDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "`AdapterView` cannot have children in XML",
            explanation = "A `ListView` is an `AdapterView` and must be configured with data " +
                    "from Java code, such as a `ListAdapter`. It cannot have children in the " +
                    "XML layout file.",
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? = Detector.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        if (!hasChildElements(element)) {
            return
        }

        val className = getClassName(element) ?: return

        if (context.driver.client.javaEvaluator.isSubClass(className, ADAPTER_VIEW, false)) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "A `ListView` or similar `AdapterView` cannot have children in XML"
            )
        }
    }

    private fun hasChildElements(element: Element): Boolean {
        val children = element.childNodes
        for (i in 0 until children.length) {
            if (children.item(i) is Element) {
                return true
            }
        }
        return false
    }

    private fun getClassName(element: Element): String? {
        val tag = element.tagName
        return when {
            tag == SdkConstants.VIEW_TAG -> {
                val cls = element.getAttribute(SdkConstants.ATTR_CLASS)
                if (cls.isNotBlank()) cls else null
            }
            tag.contains(".") -> tag
            else -> SdkConstants.ANDROID_WIDGET_PREFIX + tag
        }
    }
}
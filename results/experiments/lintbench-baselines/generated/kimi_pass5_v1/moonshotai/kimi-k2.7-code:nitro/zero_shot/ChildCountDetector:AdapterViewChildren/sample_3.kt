package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.Collection

class ChildCountDetector : LayoutDetector() {

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val cls = context.resolve(element) ?: return
        if (!context.evaluator.extendsClass(cls, ANDROID_WIDGET_ADAPTER_VIEW, false)) {
            return
        }

        if (hasChildElements(element)) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "`<AdapterView>`s cannot have children in XML"
            )
        }
    }

    private fun hasChildElements(element: Element): Boolean {
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            if (childNodes.item(i).nodeType == Node.ELEMENT_NODE) {
                return true
            }
        }
        return false
    }

    companion object {
        private const val ANDROID_WIDGET_ADAPTER_VIEW = "android.widget.AdapterView"

        @JvmField
        val ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterViews cannot have children in XML",
            explanation = """
                An `AdapterView` such as a `ListView` must be configured with data \
                from Java code, such as a `ListAdapter`. It cannot have children \
                declared in XML because those children will be ignored at runtime.
                """,
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.ERROR,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaEvaluator
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Element
import org.w3c.dom.Node

class ChildCountDetector : ResourceXmlDetector() {

    override fun getApplicableElements(): Collection<String>? = XmlScannerConstants.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        if (!hasChildElements(element)) return

        if (isAdapterView(context, element)) {
            context.report(
                ADAPTER_VIEW_CHILDREN,
                element,
                context.getLocation(element),
                "An AdapterView such as a ListView must be configured with data from Java code, " +
                    "such as a ListAdapter. You should not add children to it in the XML layout."
            )
        }
    }

    private fun hasChildElements(element: Element): Boolean {
        val childNodes = element.childNodes ?: return false
        for (i in 0 until childNodes.length) {
            if (childNodes.item(i).nodeType == Node.ELEMENT_NODE) {
                return true
            }
        }
        return false
    }

    private fun isAdapterView(context: XmlContext, element: Element): Boolean {
        val tag = element.tagName ?: return false
        if (tag.isBlank()) return false

        val className = when {
            tag == VIEW_TAG -> {
                element.getAttribute(CLASS_ATTRIBUTE)
                    ?.takeIf { it.isNotBlank() }
                    ?: return false
            }
            else -> resolveClassName(context, tag) ?: return false
        }

        return extendsAdapterView(context.evaluator, className)
    }

    private fun resolveClassName(context: XmlContext, name: String): String? {
        return if (name.contains('.')) name else resolveSimpleTag(context, name)
    }

    private fun resolveSimpleTag(context: XmlContext, tag: String): String? {
        val widgetFqcn = "android.widget.$tag"
        if (extendsAdapterView(context.evaluator, widgetFqcn)) return widgetFqcn

        val packageName = context.project.packageName
        if (packageName != null) {
            val appFqcn = "$packageName.$tag"
            if (extendsAdapterView(context.evaluator, appFqcn)) return appFqcn
        }

        return null
    }

    private fun extendsAdapterView(evaluator: JavaEvaluator, fqcn: String): Boolean {
        val cls = evaluator.findClass(fqcn) ?: return false
        return evaluator.extendsClass(cls, SdkConstants.CLASS_ADAPTERVIEW, false)
    }

    companion object {
        private const val VIEW_TAG = "view"
        private const val CLASS_ATTRIBUTE = "class"

        private val IMPLEMENTATION = Implementation(
            ChildCountDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE
        )

        @JvmField
        val ADAPTER_VIEW_CHILDREN: Issue = Issue.create(
            "AdapterViewChildren",
            "AdapterViews cannot have children in XML",
            "An AdapterView such as a ListView must be configured with data from Java code, such as a ListAdapter. You should not add children to it in the XML layout.",
            "https://developer.android.com/reference/android/widget/AdapterView.html",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            IMPLEMENTATION,
            true
        )
    }
}
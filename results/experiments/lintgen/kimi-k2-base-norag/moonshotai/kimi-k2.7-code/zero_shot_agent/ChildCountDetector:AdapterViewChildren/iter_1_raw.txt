package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
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

        if (isAdapterView(element)) {
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
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            if (childNodes.item(i).nodeType == Node.ELEMENT_NODE) {
                return true
            }
        }
        return false
    }

    private fun isAdapterView(element: Element): Boolean {
        val tag = element.tagName
        if (tag.isBlank()) return false

        val className = if (tag == VIEW_TAG) {
            element.getAttribute(CLASS_ATTRIBUTE).takeIf { it.isNotBlank() } ?: return false
        } else {
            tag
        }

        return isKnownAdapterView(className)
    }

    private fun isKnownAdapterView(className: String): Boolean {
        return when {
            className in ADAPTER_VIEW_FQCN -> true
            className.contains('.') -> {
                val simpleName = className.substringAfterLast('.')
                simpleName in ADAPTER_VIEW_SIMPLE_NAMES
            }
            else -> className in ADAPTER_VIEW_SIMPLE_NAMES
        }
    }

    companion object {
        private const val VIEW_TAG = "view"
        private const val CLASS_ATTRIBUTE = "class"

        private val ADAPTER_VIEW_SIMPLE_NAMES = setOf(
            "AdapterView",
            "ListView",
            "GridView",
            "Spinner",
            "Gallery",
            "ExpandableListView",
            "AdapterViewFlipper",
            "StackView",
            "AppCompatSpinner"
        )

        private val ADAPTER_VIEW_FQCN = setOf(
            "android.widget.AdapterView",
            "android.widget.ListView",
            "android.widget.GridView",
            "android.widget.Spinner",
            "android.widget.Gallery",
            "android.widget.ExpandableListView",
            "android.widget.AdapterViewFlipper",
            "android.widget.StackView",
            "androidx.appcompat.widget.AppCompatSpinner",
            "android.support.v7.widget.AppCompatSpinner"
        )

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
            IMPLEMENTATION
        )
    }
}
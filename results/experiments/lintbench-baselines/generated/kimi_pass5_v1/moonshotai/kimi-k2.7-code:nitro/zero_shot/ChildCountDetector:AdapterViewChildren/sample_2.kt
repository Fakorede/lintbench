package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceFolderType
import com.android.tools.lint.detector.api.ResourceXmlScanner
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class ChildCountDetector : Detector(), ResourceXmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.evaluator.extendsClass(element, ANDROID_WIDGET_ADAPTER_VIEW, false) &&
            hasChildElement(element)
        ) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "AdapterView subclasses cannot have children in XML"
            )
        }
    }

    private fun hasChildElement(element: Element): Boolean {
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                return true
            }
            child = child.nextSibling
        }
        return false
    }

    companion object {
        private const val ANDROID_WIDGET_ADAPTER_VIEW = "android.widget.AdapterView"

        @JvmField
        val ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterView cannot have children",
            explanation = """
                `AdapterView` subclasses such as `ListView`, `GridView`, `Spinner` and `Gallery` \
                must be configured with an adapter in code. They cannot contain child views in XML.
            """.trimIndent(),
            moreInfo = "https://developer.android.com/reference/android/widget/AdapterView.html",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
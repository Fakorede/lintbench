package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector(), XmlScanner {

    override fun beforeCheckRootProject(context: Context) {
    }

    override fun afterCheckRootProject(context: Context) {
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(TAG_ITEM)
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return listOf(ATTR_NAME)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode
        if (parent != null && parent.nodeName == TAG_STYLE) {
            val nameAttr = element.getAttributeNode(ATTR_NAME)
            if (nameAttr != null && nameAttr.value == "android:id") {
                val text = element.textContent
                if (text != null && text.contains("@+id/")) {
                    context.report(
                        ISSUE,
                        element,
                        context.getElementLocation(element),
                        "Defining a style which sets `android:id` to a dynamically generated id can " +
                            "cause many versions of `aapt`, the resource packaging tool, to crash. " +
                            "To work around this, declare the id explicitly with " +
                            "`<item type=\"id\" name=\"...\" />` instead."
                    )
                }
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AaptCrash",
            briefDescription = "Potential AAPT crash",
            explanation = "Defining a style which sets `android:id` to a dynamically generated id can " +
                "cause many versions of `aapt`, the resource packaging tool, to crash. " +
                "To work around this, declare the id explicitly with " +
                "`<item type=\"id\" name=\"...\" />` instead.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
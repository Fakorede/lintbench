package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ResourceCycleDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AaptCrash",
            briefDescription = "Potential AAPT crash",
            explanation = "Defining a style which sets `android:id` to a dynamically generated id can cause many versions of `aapt`, the resource packaging tool, to crash. To work around this, declare the id explicitly with `<item type=\"id\" name=\"...\" />` instead.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    override fun beforeCheckRootProject(context: Context) {
        // No initialization required
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("item")
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return null
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode
        if (parent != null && parent.nodeName == "style") {
            val nameAttr = element.getAttribute("name")
            if (nameAttr == "android:id" || nameAttr == "id") {
                val text = element.textContent?.trim()
                if (text != null && text.startsWith("@+id/")) {
                    context.report(
                        ISSUE,
                        context.getLocation(element),
                        "Defining a style which sets `android:id` to a dynamically generated id can " +
                            "cause many versions of `aapt` to crash. Declare the id explicitly with " +
                            "`<item type=\"id\" name=\"...\" />` instead."
                    )
                }
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        // No cleanup required
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        // Attribute visiting not required for this check
    }
}
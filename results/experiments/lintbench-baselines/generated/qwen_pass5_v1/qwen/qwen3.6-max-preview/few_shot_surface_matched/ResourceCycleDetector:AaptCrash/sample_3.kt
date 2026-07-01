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
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector(), XmlScanner {

    override fun beforeCheckRootProject(context: Context) {
        // Initialization hook
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("item")
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return listOf("name")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Structure validation hook
    }

    override fun afterCheckRootProject(context: Context) {
        // Cleanup hook
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (attribute.value != "android:id") return
        val element = attribute.ownerElement ?: return
        if (element.tagName != "item") return
        val parent = element.parentNode ?: return
        if (parent.nodeName != "style") return

        val value = element.textContent.trim()
        if (value.startsWith("@+id/")) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "Defining a style which sets `android:id` to a dynamically generated id can " +
                        "cause many versions of `aapt` to crash. Declare the id explicitly with " +
                        "`<item type=\"id\" name=\"...\" />` instead."
            )
        }
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
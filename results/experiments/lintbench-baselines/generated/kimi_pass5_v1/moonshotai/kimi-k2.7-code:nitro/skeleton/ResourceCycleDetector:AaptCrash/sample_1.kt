package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

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
            explanation = "Defining a style which sets `android:id` to a dynamically generated id can cause " +
                "many versions of `aapt`, the resource packaging tool, to crash. To work around this, declare " +
                "the id explicitly with `<item type=\"id\" name=\"...\" />` instead.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    override fun beforeCheckRootProject(context: Context) {
        // No project-wide setup needed.
    }

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean =
        folderType == com.android.resources.ResourceFolderType.VALUES

    override fun getApplicableElements(): Collection<String>? = listOf("style", "item")

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        if (element.tagName != "item") {
            return
        }

        val parent = element.parentNode as? org.w3c.dom.Element ?: return
        if (parent.tagName != "style") {
            return
        }

        val nameAttr = element.getAttributeNode("name") ?: return
        if (nameAttr.value != "android:id") {
            return
        }

        val value = element.textContent?.trim() ?: return
        if (value.startsWith("@+id/")) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Using a dynamically generated id in a style can cause aapt to crash. " +
                    "Declare the id explicitly with <item type=\"id\" name=\"...\" /> instead."
            )
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        // Value-based detection is handled in visitElement.
    }

    override fun afterCheckRootProject(context: Context) {
        // No project-wide cleanup needed.
    }
}
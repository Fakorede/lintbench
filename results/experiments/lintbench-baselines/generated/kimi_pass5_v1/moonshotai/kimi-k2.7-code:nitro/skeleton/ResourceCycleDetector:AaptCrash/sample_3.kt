package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
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
            explanation = """
                Defining a style which sets `android:id` to a dynamically generated id \
                (for example, `@+id/foo`) can cause many versions of `aapt` to crash.

                To work around this, declare the id explicitly as a standalone resource \
                using `<item type="id" name="..." />`, and then reference it with `@id/...` \
                in the style instead.
            """.trimIndent(),
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

    override fun getApplicableElements(): Collection<String>? = listOf("item")

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val parent = element.parentNode as? org.w3c.dom.Element ?: return
        if (parent.tagName != "style") {
            return
        }

        if (element.getAttribute("name") != "android:id") {
            return
        }

        val value = element.textContent?.trim() ?: return
        if (!value.startsWith("@+id/")) {
            return
        }

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "This style sets `android:id` to a dynamically generated id (`$value`), which can crash AAPT. " +
                "Declare the id explicitly with `<item type=\"id\" name=\"...\" />` instead.",
        )
    }

    override fun afterCheckRootProject(context: Context) {
        // No project-wide cleanup needed.
    }

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        // Not used; the check is performed on <item> elements.
    }
}
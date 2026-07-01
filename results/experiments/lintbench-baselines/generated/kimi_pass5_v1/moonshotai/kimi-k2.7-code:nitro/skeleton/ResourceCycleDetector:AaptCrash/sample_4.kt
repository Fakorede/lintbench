package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
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
        private const val TAG_STYLE = "style"
        private const val TAG_ITEM = "item"
        private const val ATTR_NAME = "name"
        private const val ANDROID_ID = "android:id"
        private const val DYNAMIC_ID_PREFIX = "@+id/"

        private val IMPLEMENTATION = Implementation(
            ResourceCycleDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AaptCrash",
            briefDescription = "Potential AAPT crash",
            explanation = """
                Defining a style which sets `android:id` to a dynamically generated id can \
                cause many versions of `aapt`, the resource packaging tool, to crash.

                To work around this, declare the id explicitly with \
                `<item type="id" name="..." />` instead.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    override fun beforeCheckRootProject(context: Context) {
        // No state to initialize.
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.VALUES

    override fun getApplicableElements(): Collection<String>? =
        listOf(TAG_STYLE, TAG_ITEM)

    override fun getApplicableAttributes(): Collection<String>? =
        listOf(ATTR_NAME)

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != TAG_ITEM) {
            return
        }

        val itemName = element.getAttribute(ATTR_NAME)
        if (itemName != ANDROID_ID) {
            return
        }

        val value = element.textContent?.trim().orEmpty()
        if (value.startsWith(DYNAMIC_ID_PREFIX)) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Setting `android:id` to a dynamically generated id (`$value`) in a style can cause AAPT to crash. Declare the id explicitly with `<item type=\"id\" name=\"...\" />` instead.",
            )
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        // Detection is performed at the element level in visitElement.
    }

    override fun afterCheckRootProject(context: Context) {
        // No state to clean up.
    }
}
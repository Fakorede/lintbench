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
            explanation = """
                Defining a style which sets `android:id` to a dynamically generated id can cause many versions of `aapt` to crash.

                To work around this, declare the id explicitly with `<item type="id" name="..." />` instead.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )

        private const val STYLE_TAG = "style"
        private const val ITEM_TAG = "item"
        private const val NAME_ATTRIBUTE = "name"
        private const val ANDROID_ID = "android:id"
        private const val DYNAMIC_ID_PREFIX = "@+id/"
    }

    override fun beforeCheckRootProject(context: Context) {
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.VALUES

    override fun getApplicableElements(): Collection<String>? =
        listOf(ITEM_TAG)

    override fun getApplicableAttributes(): Collection<String>? =
        null

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != ITEM_TAG) {
            return
        }
        if (element.parentNode?.nodeName != STYLE_TAG) {
            return
        }
        if (element.getAttribute(NAME_ATTRIBUTE) != ANDROID_ID) {
            return
        }
        val value = element.textContent.trim()
        if (value.startsWith(DYNAMIC_ID_PREFIX)) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Setting a dynamically generated id in a style can cause AAPT to crash; declare the id explicitly with <item type=\"id\" name=\"...\" /> instead.",
            )
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
    }

    override fun afterCheckRootProject(context: Context) {
    }
}
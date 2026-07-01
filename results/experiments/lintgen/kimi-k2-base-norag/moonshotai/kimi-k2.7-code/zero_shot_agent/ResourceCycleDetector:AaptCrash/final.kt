package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.NEW_ID_PREFIX
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.VALUES

    override fun getApplicableElements(): Collection<String> =
        listOf(TAG_ITEM)

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode as? Element ?: return
        if (parent.tagName != TAG_STYLE) return

        val name = element.getAttribute(ATTR_NAME) ?: return
        if (name != "android:id") return

        val value = element.getAttribute(ATTR_VALUE).ifBlank {
            element.textContent?.trim() ?: ""
        }
        if (value.startsWith(NEW_ID_PREFIX)) {
            context.report(
                ISSUE_AAPT_CRASH,
                element,
                context.getLocation(element),
                "Defining a style which sets `android:id` to a dynamically generated id can cause `aapt` to crash. " +
                    "Declare the id explicitly with `<item type=\"id\" name=\"...\" />` instead."
            )
        }
    }

    companion object {
        val ISSUE_AAPT_CRASH = Issue.create(
            id = "AaptCrash",
            briefDescription = "Potential AAPT crash",
            explanation = """
                Defining a style which sets `android:id` to a dynamically generated id can cause many
                versions of `aapt`, the resource packaging tool, to crash. To work around this, declare
                the id explicitly with `<item type="id" name="..." />` instead.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.FATAL,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
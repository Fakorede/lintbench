package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_NS_NAME_PREFIX
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_TYPE
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

/**
 * Checks for styles that set android:id to a dynamically generated id,
 * which can cause AAPT to crash.
 */
class ResourceCycleDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val CRASH = Issue.create(
            id = "AaptCrash",
            briefDescription = "Potential AAPT crash",
            explanation = """
                Defining a style which sets `android:id` to a dynamically generated id can \
                cause many versions of `aapt`, the resource packaging tool, to crash. \
                To work around this, declare the id explicitly with \
                `<item type="id" name="..." />` instead.
                """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val ANDROID_ID_ATTR = "android:id"
        private const val NEW_ID_PREFIX = "@+id/"
        private const val ANDROID_NEW_ID_PREFIX = "@+android:id/"
    }

    override fun appliesTo(folderType: com.android.resources.FolderType): Boolean {
        return folderType == com.android.resources.FolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_ITEM)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // We only care about <item> elements inside <style> elements
        val parent = element.parentNode
        if (parent == null || parent.nodeName != TAG_STYLE) {
            return
        }

        // Check if this item sets android:id
        val nameAttr = element.getAttribute(ATTR_NAME)
        if (nameAttr != ANDROID_ID_ATTR) {
            return
        }

        // Check if the value is a dynamically generated id (@+id/ or @+android:id/)
        val textContent = element.textContent?.trim() ?: return
        if (textContent.startsWith(NEW_ID_PREFIX) || textContent.startsWith(ANDROID_NEW_ID_PREFIX)) {
            val fix = fix()
                .name("Declare the id explicitly")
                .replace()
                .build()

            context.report(
                issue = CRASH,
                scope = element,
                location = context.getLocation(element),
                message = "Defining a style which sets `android:id` to a dynamically generated " +
                        "id can cause many versions of `aapt` to crash. Consider declaring the " +
                        "id explicitly with `<item type=\"id\" name=\"...\" />` instead.",
                quickfixData = null
            )
        }
    }
}
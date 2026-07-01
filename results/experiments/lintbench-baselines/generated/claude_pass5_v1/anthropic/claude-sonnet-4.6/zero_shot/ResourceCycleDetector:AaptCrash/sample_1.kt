package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_NS_NAME_PREFIX
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
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
 * Detector that finds style definitions that set android:id to a dynamically
 * generated id (using @+id/...), which can cause AAPT to crash.
 */
class ResourceCycleDetector : ResourceXmlDetector() {

    companion object {
        /** The main issue for potential AAPT crash due to dynamic IDs in styles */
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

        private const val ATTR_ANDROID_ID = "android:id"
        private const val NEW_ID_PREFIX = "@+id/"
        private const val ATTR_ID = "id"
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_ITEM)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // We only care about <item> elements inside a <style>
        val parent = element.parentNode as? Element ?: return
        if (parent.tagName != TAG_STYLE) {
            return
        }

        // Check if this item defines android:id
        val nameAttr = element.getAttribute(ATTR_NAME)
        if (nameAttr != ATTR_ANDROID_ID &&
            nameAttr != "${ANDROID_NS_NAME_PREFIX}${ATTR_ID}") {
            return
        }

        // Check if the value is a dynamically generated id (@+id/...)
        val value = element.textContent?.trim() ?: return
        if (value.startsWith(NEW_ID_PREFIX)) {
            val idName = value.substring(NEW_ID_PREFIX.length)
            val message = "This style sets `android:id` to a dynamically generated id " +
                "(`$value`), which can cause a crash in some versions of `aapt`. " +
                "Declare the id explicitly with " +
                "`<item type=\"${ResourceType.ID.getName()}\" name=\"$idName\" />` instead."
            context.report(
                issue = CRASH,
                scope = element,
                location = context.getElementLocation(element),
                message = message
            )
        }
    }
}
package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_NS_NAME_PREFIX
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.NEW_ID_PREFIX
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
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
                Defining a style which sets `android:id` to a dynamically generated id can \
                cause many versions of `aapt`, the resource packaging tool, to crash. \
                To work around this, declare the id explicitly with \
                `<item type="id" name="..." />` instead.
                """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )

        private const val ANDROID_ID = "${ANDROID_NS_NAME_PREFIX}${ATTR_ID}"
    }

    override fun beforeCheckRootProject(context: Context) {
        // Nothing to initialize
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String>? {
        return null
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return listOf(ATTR_NAME)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Not used
    }

    override fun afterCheckRootProject(context: Context) {
        // Nothing to finalize
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val element = attribute.ownerElement ?: return

        // We are looking for <item> elements inside <style> that set android:id
        // to a dynamically generated id (@+id/...)
        val tagName = element.tagName ?: return
        if (tagName != TAG_ITEM) return

        val parentElement = element.parentNode as? Element ?: return
        if (parentElement.tagName != TAG_STYLE) return

        // Check the name attribute of the item
        val nameValue = attribute.value ?: return
        if (nameValue != ANDROID_ID) return

        // Now check the text content of the item element
        val textContent = element.textContent?.trim() ?: return
        if (textContent.startsWith(NEW_ID_PREFIX)) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "This construct can potentially crash `aapt` during a build. " +
                    "Change `@+id` to `@id` and declare the id explicitly using " +
                    "`<item type=\"id\" name=\"...\" />` instead."
            )
        }
    }
}
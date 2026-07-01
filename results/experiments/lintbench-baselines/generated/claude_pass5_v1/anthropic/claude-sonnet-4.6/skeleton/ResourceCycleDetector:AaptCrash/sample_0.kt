package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_NS_NAME_PREFIX
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.ATTR_TYPE
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

        private const val ANDROID_ATTR_ID = "${ANDROID_NS_NAME_PREFIX}${ATTR_ID}"
    }

    override fun beforeCheckRootProject(context: Context) {
        // Nothing to initialize
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.VALUES

    override fun getApplicableElements(): Collection<String>? = null

    override fun getApplicableAttributes(): Collection<String>? = listOf(ATTR_NAME)

    override fun visitElement(context: XmlContext, element: Element) {
        // Not used
    }

    override fun afterCheckRootProject(context: Context) {
        // Nothing to finalize
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val element = attribute.ownerElement ?: return

        // We're looking for <item> elements inside <style> that set android:id
        // But the attribute we're visiting is "name", so let's check for the pattern:
        // <style name="...">
        //     <item name="android:id">@+id/...</item>
        // </style>

        val tagName = element.tagName ?: return

        if (tagName == TAG_ITEM) {
            val nameValue = attribute.value ?: return
            if (nameValue == ANDROID_ATTR_ID) {
                // Check if the parent is a <style> element
                val parent = element.parentNode
                if (parent is Element && parent.tagName == TAG_STYLE) {
                    // Check if the value is a dynamic id (@+id/...)
                    val textContent = element.textContent?.trim() ?: return
                    if (textContent.startsWith("@+id/")) {
                        val message = "This construct can potentially crash `aapt` during " +
                            "packaging of the resource files in older versions of `aapt`. " +
                            "Instead of defining the id here, declare the id explicitly " +
                            "with `<item type=\"id\" name=\"...\" />` instead."
                        context.report(
                            issue = ISSUE,
                            scope = element,
                            location = context.getLocation(element),
                            message = message,
                        )
                    }
                }
            }
        }
    }
}
package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_NS_NAME_PREFIX
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.NEW_ID_PREFIX
import com.android.SdkConstants.STYLE_RESOURCE_PREFIX
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

        private const val ANDROID_ID_ATTR = "$ANDROID_NS_NAME_PREFIX$ATTR_ID"
    }

    // Track whether we are currently inside a <style> element
    private var inStyleElement = false

    override fun beforeCheckRootProject(context: Context) {
        inStyleElement = false
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_STYLE)
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf(ATTR_NAME)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // We visit <style> elements; mark that we're inside one
        // The actual check is done in visitAttribute for <item> children
        // but we need to handle the case where an <item> inside a style
        // has android:id set to a @+id/ value.
        //
        // Walk through child <item> elements looking for android:id set to @+id/
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == TAG_ITEM) {
                val nameAttr = child.getAttribute(ATTR_NAME)
                if (nameAttr == ANDROID_ID_ATTR) {
                    val textContent = child.textContent?.trim() ?: ""
                    if (textContent.startsWith(NEW_ID_PREFIX)) {
                        context.report(
                            ISSUE,
                            child,
                            context.getLocation(child),
                            "This construct can potentially crash `aapt` during a " +
                                "build. Use an explicit `<item type=\"id\" " +
                                "name=\"...\"/>` instead.",
                        )
                    }
                }
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        inStyleElement = false
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        // This method handles attributes named "name" — but the main logic
        // is already handled in visitElement by walking child nodes.
        // Nothing additional needed here for the AaptCrash check.
    }
}
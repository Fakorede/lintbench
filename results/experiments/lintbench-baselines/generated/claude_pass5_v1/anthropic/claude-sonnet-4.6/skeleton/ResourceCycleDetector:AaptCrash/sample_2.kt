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
import com.android.tools.lint.detector.api.LintFix
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

        private const val ANDROID_ID_ATTR = "android:id"
        private const val NEW_ID_PREFIX = "@+id/"
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
        // Not used; detection is done in visitAttribute
    }

    override fun afterCheckRootProject(context: Context) {
        // Nothing to finalize
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        // We're looking for <item> elements inside <style> that set android:id
        // to a @+id/ value. However, the attribute we intercept is "name" on
        // <item> elements; we then inspect the element's content/value.
        //
        // Actually, the crash is triggered when a <style> contains an <item>
        // whose name is "android:id" and whose text value starts with "@+id/".

        val element = attribute.ownerElement ?: return

        // Check if we're inside a <style> element
        val tagName = element.tagName ?: return

        if (tagName == TAG_ITEM) {
            val parent = element.parentNode as? Element ?: return
            if (parent.tagName != TAG_STYLE) return

            // Check if this item's name attribute is android:id
            val nameValue = attribute.value ?: return
            if (nameValue != ANDROID_ID_ATTR && nameValue != ATTR_ID) return

            // Check the text content of this item element
            val textContent = element.textContent?.trim() ?: return
            if (textContent.startsWith(NEW_ID_PREFIX)) {
                val idName = textContent.removePrefix(NEW_ID_PREFIX)
                val fix = LintFix.create()
                    .name("Replace with explicit id declaration")
                    .replace()
                    .text(textContent)
                    .with("@id/$idName")
                    .build()

                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Avoid `@+id/` in style definitions to prevent `aapt` from crashing; " +
                        "declare the id explicitly with `<item type=\"id\" name=\"$idName\" />` instead",
                    fix,
                )
            }
        }
    }
}
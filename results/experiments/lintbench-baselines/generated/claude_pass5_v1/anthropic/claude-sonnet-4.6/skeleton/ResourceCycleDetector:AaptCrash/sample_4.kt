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

        private const val ANDROID_ID_ATTR = "${ANDROID_NS_NAME_PREFIX}${ATTR_ID}"
    }

    /** Whether we are currently inside a `<style>` element. */
    private var inStyle = false

    override fun beforeCheckRootProject(context: Context) {
        inStyle = false
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.VALUES

    override fun getApplicableElements(): Collection<String> = listOf(TAG_STYLE)

    override fun getApplicableAttributes(): Collection<String> = listOf(ATTR_NAME)

    override fun visitElement(context: XmlContext, element: Element) {
        // We're visiting a <style> element – set the flag so visitAttribute
        // knows we are inside a style block.
        if (element.tagName == TAG_STYLE) {
            inStyle = true
        }
    }

    override fun afterCheckRootProject(context: Context) {
        inStyle = false
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        // We only care about the "name" attribute on <item> elements that are
        // children of a <style> element AND whose name is "android:id".
        val element = attribute.ownerElement ?: return

        if (element.tagName != TAG_ITEM) return

        val nameValue = attribute.value ?: return
        if (nameValue != ANDROID_ID_ATTR) return

        // Check that the parent element is a <style>
        val parent = element.parentNode as? Element ?: return
        if (parent.tagName != TAG_STYLE) return

        // Check the text content of the <item> element – if it uses @+id/ syntax
        // (a dynamically generated id) then this is the problematic pattern.
        val textContent = element.textContent?.trim() ?: return
        if (textContent.startsWith(NEW_ID_PREFIX)) {
            context.report(
                issue = ISSUE,
                scope = element,
                location = context.getLocation(element),
                message = "Avoid setting `android:id` to `@+id/...` in a `<style>`: " +
                    "this can cause `aapt` to crash. Declare the id explicitly with " +
                    "`<item type=\"id\" name=\"...\" />` instead.",
            )
        }
    }
}
package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.XmlUtils
import org.w3c.dom.Document
import org.w3c.dom.Element

class RestrictionsDetector : ResourceXmlDetector(), XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != TAG_RESTRICTIONS) return

        var child = XmlUtils.getFirstSubTag(root)
        while (child != null) {
            if (child.tagName != TAG_RESTRICTION) {
                context.report(
                    ISSUE,
                    child,
                    context.getLocation(child),
                    "Expected `<restriction>` element inside `<restrictions>`"
                )
            } else {
                checkRestriction(context, child)
            }
            child = XmlUtils.getNextTag(child)
        }
    }

    private fun checkRestriction(context: XmlContext, element: Element) {
        val key = element.getAttributeNS(ANDROID_URI, ATTR_KEY)
        val title = element.getAttributeNS(ANDROID_URI, ATTR_TITLE)
        val type = element.getAttributeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)

        if (key.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element), "Missing required attribute `android:key`")
        }
        if (title.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element), "Missing required attribute `android:title`")
        }
        if (type.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element), "Missing required attribute `android:restrictionType`")
            return
        }

        if (type !in VALID_TYPES) {
            context.report(ISSUE, element, context.getLocation(element), "Invalid `android:restrictionType`: `$type`")
        }

        var child = XmlUtils.getFirstSubTag(element)
        while (child != null) {
            when (type) {
                TYPE_CHOICE, TYPE_MULTI_SELECT -> {
                    if (child.tagName != TAG_ENTRY) {
                        context.report(ISSUE, child, context.getLocation(child), "Expected `<entry>` child for `$type` restriction")
                    } else {
                        val entryKey = child.getAttributeNS(ANDROID_URI, ATTR_KEY)
                        val entryTitle = child.getAttributeNS(ANDROID_URI, ATTR_TITLE)
                        if (entryKey.isEmpty()) {
                            context.report(ISSUE, child, context.getLocation(child), "Missing required attribute `android:key` on `<entry>`")
                        }
                        if (entryTitle.isEmpty()) {
                            context.report(ISSUE, child, context.getLocation(child), "Missing required attribute `android:title` on `<entry>`")
                        }
                        if (XmlUtils.getFirstSubTag(child) != null) {
                            context.report(ISSUE, child, context.getLocation(child), "`<entry>` must not contain child elements")
                        }
                    }
                }
                TYPE_BUNDLE, TYPE_BUNDLE_ARRAY -> {
                    if (child.tagName != TAG_RESTRICTION) {
                        context.report(ISSUE, child, context.getLocation(child), "Expected `<restriction>` child for `$type` restriction")
                    } else {
                        checkRestriction(context, child)
                    }
                }
                else -> {
                    context.report(ISSUE, child, context.getLocation(child), "Restriction type `$type` should not have child elements")
                }
            }
            child = XmlUtils.getNextTag(child)
        }
    }

    companion object {
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"
        private const val TAG_ENTRY = "entry"
        private const val ATTR_KEY = "key"
        private const val ATTR_TITLE = "title"
        private const val ATTR_RESTRICTION_TYPE = "restrictionType"

        private const val TYPE_CHOICE = "choice"
        private const val TYPE_MULTI_SELECT = "multi-select"
        private const val TYPE_BUNDLE = "bundle"
        private const val TYPE_BUNDLE_ARRAY = "bundle_array"

        private val VALID_TYPES = setOf(
            "bool", TYPE_CHOICE, "hidden", "string", "integer", TYPE_BUNDLE, TYPE_BUNDLE_ARRAY, TYPE_MULTI_SELECT
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = "Ensures that an applications restrictions XML file is properly formed.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = Implementation(RestrictionsDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}
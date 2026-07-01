package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : Detector(), XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != TAG_RESTRICTIONS) {
            return
        }
        val children = root.childNodes
        var hasChildren = false
        var count = 0
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                hasChildren = true
                count = validateRestriction(context, child as Element, 1, count)
            }
        }
        if (!hasChildren) {
            context.report(
                ISSUE,
                root,
                context.getLocation(root),
                "The `<restrictions>` element must have at least one child `<restriction>`"
            )
        }
    }

    private fun validateRestriction(
        context: XmlContext,
        element: Element,
        depth: Int,
        count: Int
    ): Int {
        var currentCount = count + 1
        if (element.tagName != TAG_RESTRICTION) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Only `<restriction>` elements are allowed here"
            )
            return currentCount
        }

        if (currentCount > MAX_NUMBER_OF_NESTED_RESTRICTIONS) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "There cannot be more than $MAX_NUMBER_OF_NESTED_RESTRICTIONS nested restrictions"
            )
            return currentCount
        }

        if (depth > MAX_NESTING_DEPTH) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Restrictions cannot be nested more than $MAX_NESTING_DEPTH levels"
            )
            return currentCount
        }

        val key = element.getAttributeNS(ANDROID_URI, ATTR_KEY)
        if (key.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing `android:key` attribute"
            )
        }

        val type = element.getAttributeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
        if (type.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing `android:restrictionType` attribute"
            )
            return currentCount
        }

        when (type) {
            "bool", "string", "integer", "hidden" -> {
                ensureNoChildren(context, element)
            }
            "choice", "multi-select" -> {
                ensureNoChildren(context, element)
                val entries = element.getAttributeNS(ANDROID_URI, ATTR_ENTRIES)
                val entryValues = element.getAttributeNS(ANDROID_URI, ATTR_ENTRY_VALUES)
                if (entries.isEmpty() || entryValues.isEmpty()) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Restriction type `$type` requires `android:entries` and `android:entryValues` attributes"
                    )
                }
            }
            "bundle", "bundle_array" -> {
                val children = element.childNodes
                for (i in 0 until children.length) {
                    val child = children.item(i)
                    if (child.nodeType == Node.ELEMENT_NODE) {
                        currentCount = validateRestriction(
                            context,
                            child as Element,
                            depth + 1,
                            currentCount
                        )
                    }
                }
            }
            else -> {
                val attribute = element.getAttributeNodeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
                val location = if (attribute != null) context.getLocation(attribute) else context.getLocation(element)
                context.report(
                    ISSUE,
                    element,
                    location,
                    "Invalid restriction type `$type`"
                )
            }
        }

        return currentCount
    }

    private fun ensureNoChildren(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                context.report(
                    ISSUE,
                    child,
                    context.getLocation(child),
                    "Nested restrictions are only supported for `bundle` and `bundle_array` types"
                )
                break
            }
        }
    }

    companion object {
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"
        private const val ATTR_KEY = "key"
        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
        private const val ATTR_ENTRIES = "entries"
        private const val ATTR_ENTRY_VALUES = "entryValues"

        const val MAX_NESTING_DEPTH = 20
        const val MAX_NUMBER_OF_NESTED_RESTRICTIONS = 1000

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = """
                Ensures that an applications restrictions XML file is properly formed
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                RestrictionsDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
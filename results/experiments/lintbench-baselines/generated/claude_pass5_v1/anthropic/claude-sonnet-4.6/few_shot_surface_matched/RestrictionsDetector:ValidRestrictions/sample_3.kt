package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : ResourceXmlDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != TAG_RESTRICTIONS) {
            return
        }
        checkRestrictions(context, root, 0)
    }

    private fun checkRestrictions(context: XmlContext, element: Element, depth: Int) {
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                val tag = childElement.tagName
                if (tag == TAG_RESTRICTION) {
                    checkRestriction(context, childElement, depth)
                } else {
                    context.report(
                        ISSUE,
                        childElement,
                        context.getElementLocation(childElement),
                        "Unexpected tag `<$tag>`, expected `<$TAG_RESTRICTION>`"
                    )
                }
            }
            child = child.nextSibling
        }
    }

    private fun checkRestriction(context: XmlContext, element: Element, depth: Int) {
        // Check required attributes
        val key = element.getAttribute(ATTR_ANDROID_KEY)
        if (key.isEmpty()) {
            val keyNode = element.getAttributeNode(ATTR_ANDROID_KEY)
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "Missing required attribute `android:key`"
            )
        }

        val restrictionType = element.getAttribute(ATTR_ANDROID_RESTRICTION_TYPE)
        if (restrictionType.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "Missing required attribute `android:restrictionType`"
            )
            return
        }

        when (restrictionType) {
            TYPE_BOOL,
            TYPE_STRING,
            TYPE_INTEGER,
            TYPE_CHOICE,
            TYPE_MULTI_SELECT,
            TYPE_HIDDEN,
            TYPE_BUNDLE,
            TYPE_BUNDLE_ARRAY -> {
                // valid type
            }
            else -> {
                val typeAttr = element.getAttributeNode(ATTR_ANDROID_RESTRICTION_TYPE)
                context.report(
                    ISSUE,
                    element,
                    if (typeAttr != null) context.getValueLocation(typeAttr) else context.getElementLocation(element),
                    "Invalid `android:restrictionType` value `$restrictionType`. " +
                        "Must be one of `bool`, `string`, `integer`, `choice`, " +
                        "`multi-select`, `hidden`, `bundle`, or `bundle_array`"
                )
                return
            }
        }

        // Validate title is present (required for most types except bundle/bundle_array)
        if (restrictionType != TYPE_BUNDLE && restrictionType != TYPE_BUNDLE_ARRAY) {
            val title = element.getAttribute(ATTR_ANDROID_TITLE)
            if (title.isEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "Missing required attribute `android:title`"
                )
            }
        }

        // choice and multi-select require entries and entryValues
        if (restrictionType == TYPE_CHOICE || restrictionType == TYPE_MULTI_SELECT) {
            val entries = element.getAttribute(ATTR_ANDROID_ENTRIES)
            val entryValues = element.getAttribute(ATTR_ANDROID_ENTRY_VALUES)
            if (entries.isEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "Missing required attribute `android:entries` for " +
                        "`android:restrictionType=\"$restrictionType\"`"
                )
            }
            if (entryValues.isEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "Missing required attribute `android:entryValues` for " +
                        "`android:restrictionType=\"$restrictionType\"`"
                )
            }
        }

        // bundle and bundle_array can have nested restrictions
        if (restrictionType == TYPE_BUNDLE || restrictionType == TYPE_BUNDLE_ARRAY) {
            if (depth >= MAX_NESTING_DEPTH) {
                context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "Restrictions can be nested at most $MAX_NESTING_DEPTH levels deep"
                )
            } else {
                checkRestrictions(context, element, depth + 1)
            }
        } else {
            // Non-bundle types should not have nested restrictions
            var child = element.firstChild
            while (child != null) {
                if (child.nodeType == Node.ELEMENT_NODE) {
                    val childElement = child as Element
                    context.report(
                        ISSUE,
                        childElement,
                        context.getElementLocation(childElement),
                        "Nested `<${childElement.tagName}>` elements are only allowed inside " +
                            "`bundle` or `bundle_array` restrictions"
                    )
                }
                child = child.nextSibling
            }
        }
    }

    companion object {
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"

        private const val ATTR_ANDROID_KEY = "android:key"
        private const val ATTR_ANDROID_RESTRICTION_TYPE = "android:restrictionType"
        private const val ATTR_ANDROID_TITLE = "android:title"
        private const val ATTR_ANDROID_ENTRIES = "android:entries"
        private const val ATTR_ANDROID_ENTRY_VALUES = "android:entryValues"

        private const val TYPE_BOOL = "bool"
        private const val TYPE_STRING = "string"
        private const val TYPE_INTEGER = "integer"
        private const val TYPE_CHOICE = "choice"
        private const val TYPE_MULTI_SELECT = "multi-select"
        private const val TYPE_HIDDEN = "hidden"
        private const val TYPE_BUNDLE = "bundle"
        private const val TYPE_BUNDLE_ARRAY = "bundle_array"

        private const val MAX_NESTING_DEPTH = 1

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation =
                "Ensures that an application's restrictions XML file is properly formed.\n\n" +
                    "See https://developer.android.com/reference/android/content/RestrictionsManager.html " +
                    "for details on the restrictions XML format.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = Implementation(
                RestrictionsDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
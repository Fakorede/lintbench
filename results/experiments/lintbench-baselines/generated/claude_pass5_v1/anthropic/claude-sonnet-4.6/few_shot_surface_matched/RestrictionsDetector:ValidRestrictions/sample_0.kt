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
                checkRestrictionElement(context, childElement, depth)
            }
            child = child.nextSibling
        }
    }

    private fun checkRestrictionElement(context: XmlContext, element: Element, depth: Int) {
        val tag = element.tagName

        if (tag != TAG_RESTRICTION) {
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "Unexpected tag `<$tag>`, expected `<$TAG_RESTRICTION>`"
            )
            return
        }

        // Check required attributes
        val key = element.getAttribute(ATTR_ANDROID_KEY)
        if (key.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "Missing required attribute `android:key`"
            )
        }

        val restrictionType = element.getAttribute(ATTR_ANDROID_RESTRICTION_TYPE)
        if (restrictionType.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "Missing required attribute `android:restrictionType`"
            )
            return
        }

        if (!VALID_RESTRICTION_TYPES.contains(restrictionType)) {
            val attrNode = element.getAttributeNode(ATTR_ANDROID_RESTRICTION_TYPE)
                ?: element.getAttributeNodeNS(ANDROID_NS, "restrictionType")
            val location = if (attrNode != null) {
                context.getValueLocation(attrNode)
            } else {
                context.getElementLocation(element)
            }
            context.report(
                ISSUE,
                element,
                location,
                "Invalid `android:restrictionType` value `$restrictionType`. " +
                    "Must be one of: ${VALID_RESTRICTION_TYPES.joinToString(", ")}"
            )
            return
        }

        // Check title is required for non-bundle types
        val title = element.getAttribute(ATTR_ANDROID_TITLE)
        if (title.isNullOrEmpty() && restrictionType != TYPE_BUNDLE && restrictionType != TYPE_BUNDLE_ARRAY) {
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "Missing required attribute `android:title`"
            )
        }

        // Validate entries/entryValues for choice and multi-select types
        if (restrictionType == TYPE_CHOICE || restrictionType == TYPE_MULTI_SELECT) {
            val entries = element.getAttribute(ATTR_ANDROID_ENTRIES)
            val entryValues = element.getAttribute(ATTR_ANDROID_ENTRY_VALUES)
            if (entries.isNullOrEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "Missing required attribute `android:entries` for `$restrictionType` type"
                )
            }
            if (entryValues.isNullOrEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "Missing required attribute `android:entryValues` for `$restrictionType` type"
                )
            }
        }

        // Validate nesting: bundle and bundle_array can have children
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
            // Non-bundle types should not have child restriction elements
            var child = element.firstChild
            while (child != null) {
                if (child.nodeType == Node.ELEMENT_NODE) {
                    val childElement = child as Element
                    if (childElement.tagName == TAG_RESTRICTION) {
                        context.report(
                            ISSUE,
                            childElement,
                            context.getElementLocation(childElement),
                            "Restriction type `$restrictionType` should not have nested `<$TAG_RESTRICTION>` elements"
                        )
                    }
                }
                child = child.nextSibling
            }
        }
    }

    companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

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

        private val VALID_RESTRICTION_TYPES = setOf(
            TYPE_BOOL,
            TYPE_STRING,
            TYPE_INTEGER,
            TYPE_CHOICE,
            TYPE_MULTI_SELECT,
            TYPE_HIDDEN,
            TYPE_BUNDLE,
            TYPE_BUNDLE_ARRAY
        )

        private const val MAX_NESTING_DEPTH = 1

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation =
                """
                Ensures that an application's restrictions XML file is properly formed.

                The restrictions XML file must have a `<restrictions>` root element, and \
                each child must be a `<restriction>` element with valid `android:key`, \
                `android:restrictionType`, and `android:title` attributes (title is not \
                required for `bundle` and `bundle_array` types). The `choice` and \
                `multi-select` types also require `android:entries` and \
                `android:entryValues` attributes.

                See https://developer.android.com/reference/android/content/RestrictionsManager.html \
                for more details.
                """,
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
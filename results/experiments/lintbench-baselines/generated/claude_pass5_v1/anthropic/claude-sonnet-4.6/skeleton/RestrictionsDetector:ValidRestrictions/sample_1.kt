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

    companion object {
        private val IMPLEMENTATION = Implementation(
            RestrictionsDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = """
                Ensures that an application's restrictions XML file is properly formed.

                The restrictions XML file must contain a `<restrictions>` root element, and each \
                `<restriction>` element must have a valid `android:key`, `android:restrictionType`, \
                and `android:title` (except for `bundle` and `bundle_array` types which may have \
                nested restrictions).

                See https://developer.android.com/reference/android/content/RestrictionsManager.html \
                for details.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )

        // Root element
        private const val TAG_RESTRICTIONS = "restrictions"

        // Child element
        private const val TAG_RESTRICTION = "restriction"

        // Restriction types
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
            TYPE_BUNDLE_ARRAY,
        )

        // Attributes
        private const val ATTR_KEY = "key"
        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
        private const val ATTR_TITLE = "title"
        private const val ATTR_DESCRIPTION = "description"
        private const val ATTR_DEFAULT_VALUE = "defaultValue"
        private const val ATTR_ENTRIES = "entries"
        private const val ATTR_ENTRY_VALUES = "entryValues"

        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

        // Maximum nesting depth for bundle restrictions
        private const val MAX_NESTING_DEPTH = 255
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.XML

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return

        // Check that the root element is <restrictions>
        if (root.tagName != TAG_RESTRICTIONS) {
            // Only validate files that look like restriction files or are named appropriately
            // We'll only process files whose root is <restrictions>
            return
        }

        // Visit all child <restriction> elements
        visitRestrictionChildren(context, root, 0)
    }

    private fun visitRestrictionChildren(context: XmlContext, parent: Element, depth: Int) {
        var child = parent.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val element = child as Element
                if (element.tagName == TAG_RESTRICTION) {
                    visitRestriction(context, element, depth + 1)
                } else {
                    context.report(
                        ISSUE,
                        element,
                        context.getElementLocation(element),
                        "Unexpected element `<${element.tagName}>`, expected `<$TAG_RESTRICTION>`",
                    )
                }
            }
            child = child.nextSibling
        }
    }

    private fun visitRestriction(context: XmlContext, element: Element, depth: Int) {
        if (depth > MAX_NESTING_DEPTH) {
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "Restriction nesting depth exceeds maximum allowed depth ($MAX_NESTING_DEPTH)",
            )
            return
        }

        // Validate android:key attribute
        val key = element.getAttributeNS(ANDROID_NS, ATTR_KEY)
        if (key.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "Missing required attribute `android:key`",
            )
        }

        // Validate android:restrictionType attribute
        val restrictionType = element.getAttributeNS(ANDROID_NS, ATTR_RESTRICTION_TYPE)
        if (restrictionType.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "Missing required attribute `android:restrictionType`",
            )
            return
        }

        if (restrictionType !in VALID_RESTRICTION_TYPES) {
            val typeAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_RESTRICTION_TYPE)
            val location = if (typeAttr != null) {
                context.getValueLocation(typeAttr)
            } else {
                context.getElementLocation(element)
            }
            context.report(
                ISSUE,
                element,
                location,
                "Invalid restriction type `$restrictionType`. " +
                    "Must be one of: ${VALID_RESTRICTION_TYPES.joinToString(", ") { "`$it`" }}",
            )
            return
        }

        // Validate android:title attribute (required for non-bundle types)
        val title = element.getAttributeNS(ANDROID_NS, ATTR_TITLE)
        if (title.isNullOrEmpty() && restrictionType != TYPE_BUNDLE && restrictionType != TYPE_BUNDLE_ARRAY) {
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "Missing required attribute `android:title`",
            )
        }

        // For choice and multi-select types, validate entries and entryValues
        if (restrictionType == TYPE_CHOICE || restrictionType == TYPE_MULTI_SELECT) {
            val entries = element.getAttributeNS(ANDROID_NS, ATTR_ENTRIES)
            val entryValues = element.getAttributeNS(ANDROID_NS, ATTR_ENTRY_VALUES)

            if (entries.isNullOrEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "Restriction type `$restrictionType` requires `android:entries` attribute",
                )
            }

            if (entryValues.isNullOrEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "Restriction type `$restrictionType` requires `android:entryValues` attribute",
                )
            }
        }

        // For bundle and bundle_array types, recurse into children
        if (restrictionType == TYPE_BUNDLE || restrictionType == TYPE_BUNDLE_ARRAY) {
            visitRestrictionChildren(context, element, depth)
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
                            "Restriction type `$restrictionType` should not have nested `<$TAG_RESTRICTION>` elements; " +
                                "only `$TYPE_BUNDLE` and `$TYPE_BUNDLE_ARRAY` types can have nested restrictions",
                        )
                    }
                }
                child = child.nextSibling
            }
        }
    }
}
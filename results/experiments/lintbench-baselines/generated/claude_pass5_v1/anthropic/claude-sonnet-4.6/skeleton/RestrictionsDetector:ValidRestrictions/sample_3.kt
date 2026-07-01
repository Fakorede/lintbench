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

                The restrictions XML file must have a `<restrictions>` root element, and each \
                child `<restriction>` element must have an `android:key` and `android:restrictionType` \
                attribute. Valid restriction types are: `bool`, `string`, `integer`, `choice`, \
                `multi-select`, `hidden`, `bundle`, and `bundle_array`. Restrictions of type \
                `choice` and `multi-select` must have an `android:entries` and \
                `android:entryValues` attribute. Nested restrictions are only allowed inside \
                `bundle` and `bundle_array` restriction types.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )

        // XML tag and attribute names
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"

        private const val ATTR_KEY = "android:key"
        private const val ATTR_RESTRICTION_TYPE = "android:restrictionType"
        private const val ATTR_TITLE = "android:title"
        private const val ATTR_ENTRIES = "android:entries"
        private const val ATTR_ENTRY_VALUES = "android:entryValues"

        // Valid restriction types
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

        private val TYPES_REQUIRING_ENTRIES = setOf(TYPE_CHOICE, TYPE_MULTI_SELECT)
        private val TYPES_ALLOWING_NESTED = setOf(TYPE_BUNDLE, TYPE_BUNDLE_ARRAY)
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return

        // The root element must be <restrictions>
        if (root.tagName != TAG_RESTRICTIONS) {
            context.report(
                ISSUE,
                root,
                context.getLocation(root),
                "Expected `<$TAG_RESTRICTIONS>` as the root tag, but found `<${root.tagName}>`",
            )
            return
        }

        // Validate all direct children of <restrictions>
        validateRestrictionChildren(context, root, isTopLevel = true)
    }

    private fun validateRestrictionChildren(
        context: XmlContext,
        parent: Element,
        isTopLevel: Boolean,
    ) {
        var child = parent.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val element = child as Element
                if (element.tagName == TAG_RESTRICTION) {
                    validateRestriction(context, element)
                } else {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Unexpected tag `<${element.tagName}>`: expected `<$TAG_RESTRICTION>`",
                    )
                }
            }
            child = child.nextSibling
        }
    }

    private fun validateRestriction(context: XmlContext, element: Element) {
        // Validate android:key attribute
        val key = getAttributeValue(element, "key")
        if (key == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing required attribute `$ATTR_KEY` in `<$TAG_RESTRICTION>`",
            )
        } else if (key.isBlank()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Attribute `$ATTR_KEY` must not be empty",
            )
        }

        // Validate android:restrictionType attribute
        val restrictionType = getAttributeValue(element, "restrictionType")
        if (restrictionType == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing required attribute `$ATTR_RESTRICTION_TYPE` in `<$TAG_RESTRICTION>`",
            )
            // Still validate children if possible, but we can't check nesting rules without type
            validateNestedRestrictions(context, element, allowNested = false)
            return
        }

        if (restrictionType !in VALID_RESTRICTION_TYPES) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Invalid restriction type `$restrictionType`. " +
                    "Must be one of: ${VALID_RESTRICTION_TYPES.sorted().joinToString(", ")}",
            )
        }

        // Validate android:title is present (required for most types, except hidden)
        val title = getAttributeValue(element, "title")
        if (title == null && restrictionType != TYPE_HIDDEN) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing required attribute `$ATTR_TITLE` in `<$TAG_RESTRICTION>`",
            )
        }

        // Validate entries and entryValues for choice and multi-select types
        if (restrictionType in TYPES_REQUIRING_ENTRIES) {
            val entries = getAttributeValue(element, "entries")
            if (entries == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Restriction type `$restrictionType` requires attribute `$ATTR_ENTRIES`",
                )
            }

            val entryValues = getAttributeValue(element, "entryValues")
            if (entryValues == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Restriction type `$restrictionType` requires attribute `$ATTR_ENTRY_VALUES`",
                )
            }
        }

        // Validate nested <restriction> elements
        val allowNested = restrictionType in TYPES_ALLOWING_NESTED
        validateNestedRestrictions(context, element, allowNested)
    }

    private fun validateNestedRestrictions(
        context: XmlContext,
        parent: Element,
        allowNested: Boolean,
    ) {
        var child = parent.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val element = child as Element
                if (element.tagName == TAG_RESTRICTION) {
                    if (!allowNested) {
                        val parentType = getAttributeValue(parent, "restrictionType") ?: "unknown"
                        context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "Nested `<$TAG_RESTRICTION>` elements are only allowed inside " +
                                "`$TYPE_BUNDLE` or `$TYPE_BUNDLE_ARRAY` restrictions, " +
                                "but the parent has type `$parentType`",
                        )
                    } else {
                        validateRestriction(context, element)
                    }
                } else {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Unexpected tag `<${element.tagName}>` inside `<$TAG_RESTRICTION>`",
                    )
                }
            }
            child = child.nextSibling
        }
    }

    /**
     * Returns the value of an attribute in the Android namespace, or null if not present or empty.
     * Checks both "android:attrName" form and the actual namespace URI form.
     */
    private fun getAttributeValue(element: Element, localName: String): String? {
        // Try with android namespace URI
        val nsValue = element.getAttributeNS(
            "http://schemas.android.com/apk/res/android",
            localName,
        )
        if (nsValue.isNotEmpty()) return nsValue

        // Try with "android:" prefix (no namespace processing)
        val prefixedValue = element.getAttribute("android:$localName")
        if (prefixedValue.isNotEmpty()) return prefixedValue

        return null
    }
}
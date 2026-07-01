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
                checkRestriction(context, childElement, depth)
            }
            child = child.nextSibling
        }
    }

    private fun checkRestriction(context: XmlContext, element: Element, depth: Int) {
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
        val key = element.getAttribute(ATTR_KEY)
        if (key.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "Missing required attribute `$ATTR_KEY`"
            )
        }

        val restrictionType = element.getAttribute(ATTR_RESTRICTION_TYPE)
        if (restrictionType.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "Missing required attribute `$ATTR_RESTRICTION_TYPE`"
            )
            return
        }

        if (!VALID_RESTRICTION_TYPES.contains(restrictionType)) {
            val attr = element.getAttributeNode(ATTR_RESTRICTION_TYPE)
            context.report(
                ISSUE,
                element,
                if (attr != null) context.getValueLocation(attr) else context.getElementLocation(element),
                "Invalid restriction type `$restrictionType`, must be one of ${VALID_RESTRICTION_TYPES.joinToString(", ") { "`$it`" }}"
            )
            return
        }

        // Check title is present (required for all types)
        val title = element.getAttribute(ATTR_TITLE)
        if (title.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "Missing required attribute `$ATTR_TITLE`"
            )
        }

        // For choice and multi-select, entries and entryValues are required
        if (restrictionType == TYPE_CHOICE || restrictionType == TYPE_MULTI_SELECT) {
            val entries = element.getAttribute(ATTR_ENTRIES)
            if (entries.isNullOrEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "Restriction type `$restrictionType` requires attribute `$ATTR_ENTRIES`"
                )
            }
            val entryValues = element.getAttribute(ATTR_ENTRY_VALUES)
            if (entryValues.isNullOrEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "Restriction type `$restrictionType` requires attribute `$ATTR_ENTRY_VALUES`"
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
            // Non-bundle types should not have child restriction elements
            var child = element.firstChild
            while (child != null) {
                if (child.nodeType == Node.ELEMENT_NODE) {
                    val childElement = child as Element
                    context.report(
                        ISSUE,
                        childElement,
                        context.getElementLocation(childElement),
                        "Restriction type `$restrictionType` should not have nested `<$TAG_RESTRICTION>` elements"
                    )
                    break
                }
                child = child.nextSibling
            }
        }
    }

    companion object {
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"

        private const val ATTR_KEY = "android:key"
        private const val ATTR_TITLE = "android:title"
        private const val ATTR_RESTRICTION_TYPE = "android:restrictionType"
        private const val ATTR_ENTRIES = "android:entries"
        private const val ATTR_ENTRY_VALUES = "android:entryValues"

        private const val TYPE_BOOL = "bool"
        private const val TYPE_STRING = "string"
        private const val TYPE_INTEGER = "integer"
        private const val TYPE_CHOICE = "choice"
        private const val TYPE_MULTI_SELECT = "multi-select"
        private const val TYPE_HIDDEN = "hidden"
        private const val TYPE_BUNDLE = "bundle"
        private const val TYPE_BUNDLE_ARRAY = "bundle_array"

        private const val MAX_NESTING_DEPTH = 1

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

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation =
                "Ensures that an application's restrictions XML file is properly formed.\n\n" +
                "The restrictions XML file must have a `<restrictions>` root element, " +
                "and each `<restriction>` element must have a valid `android:key`, " +
                "`android:title`, and `android:restrictionType` attribute. " +
                "The `choice` and `multi-select` types also require `android:entries` " +
                "and `android:entryValues` attributes. " +
                "See https://developer.android.com/reference/android/content/RestrictionsManager.html " +
                "for more details.",
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
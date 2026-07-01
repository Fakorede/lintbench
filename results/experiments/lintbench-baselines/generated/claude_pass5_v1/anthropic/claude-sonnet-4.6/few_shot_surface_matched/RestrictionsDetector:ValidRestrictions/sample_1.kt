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

        // The root element must be <restrictions>
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
        if (key.isNullOrEmpty()) {
            val attr = element.getAttributeNode(ATTR_ANDROID_KEY)
            if (attr == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "Missing required attribute `android:key`"
                )
            }
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
            val attr = element.getAttributeNode(ATTR_ANDROID_RESTRICTION_TYPE)
            if (attr != null) {
                context.report(
                    ISSUE,
                    element,
                    context.getValueLocation(attr),
                    "Invalid restriction type `$restrictionType`, must be one of: " +
                        VALID_RESTRICTION_TYPES.joinToString(", ") { "`$it`" }
                )
            }
            return
        }

        // Check title is present (required for all non-bundle types)
        if (restrictionType != TYPE_BUNDLE && restrictionType != TYPE_BUNDLE_ARRAY) {
            val title = element.getAttribute(ATTR_ANDROID_TITLE)
            if (title.isNullOrEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "Missing required attribute `android:title`"
                )
            }
        }

        // Check for defaultValue type consistency
        val defaultValue = element.getAttribute(ATTR_ANDROID_DEFAULT_VALUE)
        if (!defaultValue.isNullOrEmpty()) {
            when (restrictionType) {
                TYPE_BOOLEAN -> {
                    if (defaultValue != "true" && defaultValue != "false") {
                        val attr = element.getAttributeNode(ATTR_ANDROID_DEFAULT_VALUE)
                        if (attr != null) {
                            context.report(
                                ISSUE,
                                element,
                                context.getValueLocation(attr),
                                "Invalid default value `$defaultValue` for type `$TYPE_BOOLEAN`: " +
                                    "must be `true` or `false`"
                            )
                        }
                    }
                }
                TYPE_INTEGER -> {
                    if (defaultValue.toIntOrNull() == null) {
                        val attr = element.getAttributeNode(ATTR_ANDROID_DEFAULT_VALUE)
                        if (attr != null) {
                            context.report(
                                ISSUE,
                                element,
                                context.getValueLocation(attr),
                                "Invalid default value `$defaultValue` for type `$TYPE_INTEGER`: " +
                                    "must be an integer"
                            )
                        }
                    }
                }
            }
        }

        // choice and multi-select must have entries and entry-values
        if (restrictionType == TYPE_CHOICE || restrictionType == TYPE_MULTI_SELECT) {
            val entries = element.getAttribute(ATTR_ANDROID_ENTRIES)
            val entryValues = element.getAttribute(ATTR_ANDROID_ENTRY_VALUES)

            if (entries.isNullOrEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "Restriction type `$restrictionType` requires attribute `android:entries`"
                )
            }
            if (entryValues.isNullOrEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "Restriction type `$restrictionType` requires attribute `android:entryValues`"
                )
            }
        }

        // bundle and bundle_array must have nested restrictions
        if (restrictionType == TYPE_BUNDLE || restrictionType == TYPE_BUNDLE_ARRAY) {
            val hasChildren = hasChildRestrictions(element)
            if (!hasChildren) {
                context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "Restriction type `$restrictionType` must have nested `<$TAG_RESTRICTION>` elements"
                )
            } else {
                // Recursively check nested restrictions
                checkRestrictions(context, element, depth + 1)
            }
        } else {
            // Non-bundle types should not have nested restrictions
            if (hasChildRestrictions(element)) {
                context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "Restriction type `$restrictionType` should not have nested `<$TAG_RESTRICTION>` elements"
                )
            }
        }
    }

    private fun hasChildRestrictions(element: Element): Boolean {
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE && (child as Element).tagName == TAG_RESTRICTION) {
                return true
            }
            child = child.nextSibling
        }
        return false
    }

    companion object {
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"

        private const val ATTR_ANDROID_KEY = "android:key"
        private const val ATTR_ANDROID_TITLE = "android:title"
        private const val ATTR_ANDROID_RESTRICTION_TYPE = "android:restrictionType"
        private const val ATTR_ANDROID_DEFAULT_VALUE = "android:defaultValue"
        private const val ATTR_ANDROID_ENTRIES = "android:entries"
        private const val ATTR_ANDROID_ENTRY_VALUES = "android:entryValues"

        private const val TYPE_BOOL = "bool"
        private const val TYPE_BOOLEAN = "boolean"
        private const val TYPE_STRING = "string"
        private const val TYPE_INTEGER = "integer"
        private const val TYPE_CHOICE = "choice"
        private const val TYPE_MULTI_SELECT = "multi-select"
        private const val TYPE_HIDDEN = "hidden"
        private const val TYPE_BUNDLE = "bundle"
        private const val TYPE_BUNDLE_ARRAY = "bundle_array"

        private val VALID_RESTRICTION_TYPES = setOf(
            TYPE_BOOL,
            TYPE_BOOLEAN,
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
                """
                Ensures that an application's restrictions XML file is properly formed.

                The restrictions file must have a `<restrictions>` root element, and each \
                `<restriction>` element must have a valid `android:key`, a valid \
                `android:restrictionType`, and appropriate child elements or attributes \
                depending on the restriction type.

                See https://developer.android.com/reference/android/content/RestrictionsManager.html \
                for details.
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
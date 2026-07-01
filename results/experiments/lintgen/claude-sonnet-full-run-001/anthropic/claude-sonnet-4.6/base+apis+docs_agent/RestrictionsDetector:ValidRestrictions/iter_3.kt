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

    companion object {
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"

        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
        private const val ATTR_TITLE = "title"
        private const val ATTR_KEY = "key"
        private const val ATTR_DEFAULT_VALUE = "defaultValue"
        private const val ATTR_ENTRIES = "entries"
        private const val ATTR_ENTRY_VALUES = "entryValues"
        private const val ATTR_DESCRIPTION = "description"

        private const val TYPE_BOOL = "bool"
        private const val TYPE_STRING = "string"
        private const val TYPE_INTEGER = "integer"
        private const val TYPE_CHOICE = "choice"
        private const val TYPE_MULTI_SELECT = "multi-select"
        private const val TYPE_HIDDEN = "hidden"
        private const val TYPE_BUNDLE = "bundle"
        private const val TYPE_BUNDLE_ARRAY = "bundle_array"

        private val VALID_TYPES = setOf(
            TYPE_BOOL,
            TYPE_STRING,
            TYPE_INTEGER,
            TYPE_CHOICE,
            TYPE_MULTI_SELECT,
            TYPE_HIDDEN,
            TYPE_BUNDLE,
            TYPE_BUNDLE_ARRAY
        )

        const val MAX_NESTING_DEPTH = 20
        const val MAX_NUMBER_OF_NESTED_RESTRICTIONS = 1000

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = """
                Ensures that an application's restrictions XML file is properly formed.

                The restrictions XML file must follow the format described in the \
                `RestrictionsManager` documentation. Each `<restriction>` element must have \
                a valid `android:key`, `android:restrictionType`, and `android:title` \
                (except for `bundle` and `bundle_array` types which may have nested restrictions). \
                Choice and multi-select types must have `android:entries` and \
                `android:entryValues` attributes.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            moreInfo = "https://developer.android.com/reference/android/content/RestrictionsManager.html",
            implementation = Implementation(
                RestrictionsDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return

        if (root.tagName != TAG_RESTRICTIONS) {
            return
        }

        val counter = intArrayOf(0)
        checkRestrictions(context, root, true, 0, counter)
    }

    private fun getChildElements(parent: Element): List<Element> {
        val result = mutableListOf<Element>()
        var child = parent.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                result.add(child as Element)
            }
            child = child.nextSibling
        }
        return result
    }

    private fun checkRestrictions(
        context: XmlContext,
        parent: Element,
        isRoot: Boolean,
        depth: Int,
        counter: IntArray
    ) {
        if (depth > MAX_NESTING_DEPTH) {
            context.report(
                ISSUE,
                parent,
                context.getLocation(parent),
                "Restrictions nesting depth exceeds $MAX_NESTING_DEPTH"
            )
            return
        }

        val children = getChildElements(parent)
        for (element in children) {
            if (element.tagName == TAG_RESTRICTION) {
                counter[0]++
                if (counter[0] > MAX_NUMBER_OF_NESTED_RESTRICTIONS) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Too many restrictions: number of restrictions exceeds $MAX_NUMBER_OF_NESTED_RESTRICTIONS"
                    )
                    return
                }
                checkRestrictionElement(context, element, depth + 1, counter)
            } else {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Unexpected tag `<${element.tagName}>`, expected `<$TAG_RESTRICTION>`"
                )
            }
        }
    }

    private fun isResourceReference(value: String): Boolean {
        return value.startsWith("@") || value.startsWith("?")
    }

    private fun checkRestrictionElement(
        context: XmlContext,
        element: Element,
        depth: Int,
        counter: IntArray
    ) {
        // Check for required android:key attribute
        val keyAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_KEY)
        val key = keyAttr?.value

        if (keyAttr == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing required attribute `android:key`"
            )
        } else if (key.isNullOrEmpty()) {
            context.report(
                ISSUE,
                keyAttr,
                context.getLocation(keyAttr),
                "The `android:key` attribute cannot be empty"
            )
        } else if (isResourceReference(key)) {
            // Keys must be literal strings, not resource references
            context.report(
                ISSUE,
                keyAttr,
                context.getLocation(keyAttr),
                "The `android:key` attribute cannot be a resource reference, it must be a literal string"
            )
        }

        // Check for required android:restrictionType attribute
        val restrictionTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
        val restrictionType = restrictionTypeAttr?.value

        if (restrictionTypeAttr == null || restrictionType.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing required attribute `android:restrictionType`"
            )
            return
        }

        // Validate restriction type value
        if (!VALID_TYPES.contains(restrictionType)) {
            context.report(
                ISSUE,
                restrictionTypeAttr,
                context.getLocation(restrictionTypeAttr),
                "Invalid restriction type `$restrictionType`. Must be one of: ${VALID_TYPES.joinToString(", ")}"
            )
            return
        }

        // For bundle and bundle_array types
        if (restrictionType == TYPE_BUNDLE || restrictionType == TYPE_BUNDLE_ARRAY) {
            // Check for required android:title attribute for bundle types
            val titleAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_TITLE)
            val title = titleAttr?.value

            if (titleAttr == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing required attribute `android:title`"
                )
            } else if (title.isNullOrEmpty()) {
                context.report(
                    ISSUE,
                    titleAttr,
                    context.getLocation(titleAttr),
                    "The `android:title` attribute cannot be empty"
                )
            }

            // bundle and bundle_array must have children
            val children = getChildElements(element)
            if (children.isEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "`$restrictionType` restriction must have nested `<restriction>` children"
                )
            } else {
                checkRestrictions(context, element, false, depth, counter)
            }
            return
        }

        // Check for required android:title attribute
        val titleAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_TITLE)
        val title = titleAttr?.value

        if (titleAttr == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing required attribute `android:title`"
            )
        } else if (title.isNullOrEmpty()) {
            context.report(
                ISSUE,
                titleAttr,
                context.getLocation(titleAttr),
                "The `android:title` attribute cannot be empty"
            )
        }

        // hidden type requires defaultValue
        if (restrictionType == TYPE_HIDDEN) {
            val defaultValueAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_DEFAULT_VALUE)
            if (defaultValueAttr == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing required attribute `android:defaultValue` for restriction type `$restrictionType`"
                )
            }
        }

        // For choice and multi-select types, entries and entryValues are required
        if (restrictionType == TYPE_CHOICE || restrictionType == TYPE_MULTI_SELECT) {
            val entriesAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ENTRIES)
            if (entriesAttr == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing required attribute `android:entries` for restriction type `$restrictionType`"
                )
            } else if (entriesAttr.value.isNullOrEmpty()) {
                context.report(
                    ISSUE,
                    entriesAttr,
                    context.getLocation(entriesAttr),
                    "The `android:entries` attribute cannot be empty for restriction type `$restrictionType`"
                )
            }

            val entryValuesAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ENTRY_VALUES)
            if (entryValuesAttr == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing required attribute `android:entryValues` for restriction type `$restrictionType`"
                )
            } else if (entryValuesAttr.value.isNullOrEmpty()) {
                context.report(
                    ISSUE,
                    entryValuesAttr,
                    context.getLocation(entryValuesAttr),
                    "The `android:entryValues` attribute cannot be empty for restriction type `$restrictionType`"
                )
            }
        }

        // Validate that non-bundle types don't have child restriction elements
        val children = getChildElements(element)
        if (children.isNotEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Restriction type `$restrictionType` should not have nested `<restriction>` children"
            )
        }
    }
}
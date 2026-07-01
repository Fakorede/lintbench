package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class RestrictionsDetector : ResourceXmlDetector() {

    companion object {
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"

        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
        private const val ATTR_TITLE = "title"
        private const val ATTR_DESCRIPTION = "description"
        private const val ATTR_KEY = "key"
        private const val ATTR_DEFAULT_VALUE = "defaultValue"
        private const val ATTR_ENTRIES = "entries"
        private const val ATTR_ENTRY_VALUES = "entryValues"

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

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = """
                Ensures that an application's restrictions XML file is properly formed.

                The restrictions XML file must:
                * Have a `<restrictions>` root tag
                * Each `<restriction>` element must have a `android:key` attribute
                * Each `<restriction>` element must have a `android:restrictionType` attribute with a valid value
                * Restrictions of type `choice` or `multi-select` must have `android:entries` and `android:entryValues`
                * `bundle` and `bundle_array` restrictions may contain nested `<restriction>` elements
                """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                RestrictionsDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            ),
            moreInfo = "https://developer.android.com/reference/android/content/RestrictionsManager.html"
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_RESTRICTIONS, TAG_RESTRICTION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_RESTRICTIONS -> checkRestrictionsRoot(context, element)
            TAG_RESTRICTION -> checkRestriction(context, element)
        }
    }

    private fun checkRestrictionsRoot(context: XmlContext, element: Element) {
        val parent = element.parentNode
        if (parent != null && parent.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "The `<restrictions>` element should be the root element"
            )
        }
    }

    private fun checkRestriction(context: XmlContext, element: Element) {
        val parent = element.parentNode
        if (parent != null && parent.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
            val parentTag = (parent as Element).tagName
            if (parentTag != TAG_RESTRICTIONS && parentTag != TAG_RESTRICTION) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "A `<restriction>` element must be a child of `<restrictions>` or another `<restriction>` element"
                )
                return
            }

            // If nested inside a restriction, the parent must be bundle or bundle_array
            if (parentTag == TAG_RESTRICTION) {
                val parentType = parent.getAttributeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
                if (parentType != TYPE_BUNDLE && parentType != TYPE_BUNDLE_ARRAY) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Nested `<restriction>` elements are only allowed inside `bundle` or `bundle_array` restrictions"
                    )
                }
            }
        }

        // Check for required 'key' attribute
        val key = element.getAttributeNS(ANDROID_URI, ATTR_KEY)
        if (key.isNullOrEmpty()) {
            // Also check without namespace for compatibility
            val keyNoNs = element.getAttribute("android:$ATTR_KEY")
            if (keyNoNs.isNullOrEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing required attribute `android:key`"
                )
            }
        }

        // Check for required 'restrictionType' attribute
        val restrictionType = element.getAttributeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
        if (restrictionType.isNullOrEmpty()) {
            val typeNoNs = element.getAttribute("android:$ATTR_RESTRICTION_TYPE")
            if (typeNoNs.isNullOrEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing required attribute `android:restrictionType`"
                )
            } else {
                checkRestrictionType(context, element, typeNoNs)
            }
        } else {
            checkRestrictionType(context, element, restrictionType)
        }

        // Check for required 'title' attribute (not required for hidden type)
        val type = if (restrictionType.isNullOrEmpty()) {
            element.getAttribute("android:$ATTR_RESTRICTION_TYPE")
        } else {
            restrictionType
        }

        if (type != TYPE_HIDDEN && type != TYPE_BUNDLE && type != TYPE_BUNDLE_ARRAY) {
            val title = element.getAttributeNS(ANDROID_URI, ATTR_TITLE)
            if (title.isNullOrEmpty()) {
                val titleNoNs = element.getAttribute("android:$ATTR_TITLE")
                if (titleNoNs.isNullOrEmpty()) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Missing required attribute `android:title`"
                    )
                }
            }
        }
    }

    private fun checkRestrictionType(context: XmlContext, element: Element, type: String) {
        if (type !in VALID_TYPES) {
            val typeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
                ?: element.getAttributeNode("android:$ATTR_RESTRICTION_TYPE")
            val location = if (typeAttr != null) {
                context.getLocation(typeAttr)
            } else {
                context.getLocation(element)
            }
            context.report(
                ISSUE,
                element,
                location,
                "Invalid `android:restrictionType` value `$type`. Must be one of: ${VALID_TYPES.joinToString(", ")}"
            )
            return
        }

        // For choice and multi-select, entries and entryValues are required
        if (type == TYPE_CHOICE || type == TYPE_MULTI_SELECT) {
            val entries = element.getAttributeNS(ANDROID_URI, ATTR_ENTRIES)
            val entriesNoNs = element.getAttribute("android:$ATTR_ENTRIES")
            if (entries.isNullOrEmpty() && entriesNoNs.isNullOrEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Restriction type `$type` requires `android:entries` attribute"
                )
            }

            val entryValues = element.getAttributeNS(ANDROID_URI, ATTR_ENTRY_VALUES)
            val entryValuesNoNs = element.getAttribute("android:$ATTR_ENTRY_VALUES")
            if (entryValues.isNullOrEmpty() && entryValuesNoNs.isNullOrEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Restriction type `$type` requires `android:entryValues` attribute"
                )
            }
        }
    }
}
package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.Location
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = "Ensures that an applications restrictions XML file is properly formed",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                RestrictionsDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"

        private const val ATTR_KEY = "key"
        private const val ATTR_TITLE = "title"
        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
        private const val ATTR_DEFAULT_VALUE = "defaultValue"
        private const val ATTR_ENTRIES = "entries"
        private const val ATTR_ENTRY_VALUES = "entryValues"

        private const val TYPE_BOOLEAN = "boolean"
        private const val TYPE_INTEGER = "integer"
        private const val TYPE_STRING = "string"
        private const val TYPE_CHOICE = "choice"
        private const val TYPE_MULTI_SELECT = "multi-select"
        private const val TYPE_HIDDEN = "hidden"
        private const val TYPE_BUNDLE = "bundle"
        private const val TYPE_BUNDLE_ARRAY = "bundle_array"
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_RESTRICTIONS)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != TAG_RESTRICTIONS) return

        val parent = element.parentNode
        if (parent != null && parent.nodeType != Node.DOCUMENT_NODE) {
            return
        }

        val keys = mutableSetOf<String>()
        var child = element.firstChild
        while (child != null) {
            if (child is Element) {
                val tagName = child.tagName
                if (tagName != TAG_RESTRICTION) {
                    context.report(
                        ISSUE,
                        child,
                        context.getLocation(child),
                        "Only `<restriction>` elements are allowed under `<restrictions>`"
                    )
                } else {
                    validateRestriction(context, child, keys)
                }
            }
            child = child.nextSibling
        }
    }

    private fun getAttrLocation(context: XmlContext, element: Element, namespace: String, localName: String): Location {
        val attr = element.getAttributeNodeNS(namespace, localName)
        return if (attr != null) context.getLocation(attr) else context.getNameLocation(element)
    }

    private fun validateRestriction(context: XmlContext, element: Element, keys: MutableSet<String>) {
        val key = element.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_KEY)
        if (key.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `android:key` attribute"
            )
        } else {
            if (!keys.add(key)) {
                context.report(
                    ISSUE,
                    element,
                    getAttrLocation(context, element, SdkConstants.ANDROID_URI, ATTR_KEY),
                    "Duplicate restriction key `$key`"
                )
            }
        }

        val type = element.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_RESTRICTION_TYPE)
        if (type.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `android:restrictionType` attribute"
            )
            return
        }

        when (type) {
            TYPE_BOOLEAN, TYPE_INTEGER, TYPE_STRING, TYPE_CHOICE, TYPE_MULTI_SELECT, TYPE_HIDDEN, TYPE_BUNDLE, TYPE_BUNDLE_ARRAY -> {
                // Valid type
            }
            else -> {
                context.report(
                    ISSUE,
                    element,
                    getAttrLocation(context, element, SdkConstants.ANDROID_URI, ATTR_RESTRICTION_TYPE),
                    "Invalid `android:restrictionType` `$type`"
                )
                return
            }
        }

        if (type != TYPE_HIDDEN && type != TYPE_BUNDLE && type != TYPE_BUNDLE_ARRAY) {
            if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, ATTR_TITLE)) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `android:title` attribute"
                )
            }
        }

        if (type == TYPE_CHOICE || type == TYPE_MULTI_SELECT) {
            if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, ATTR_ENTRIES)) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `android:entries` attribute for restriction type `$type`"
                )
            }
            if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, ATTR_ENTRY_VALUES)) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `android:entryValues` attribute for restriction type `$type`"
                )
            }
        }

        if (type == TYPE_HIDDEN) {
            if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, ATTR_DEFAULT_VALUE)) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `android:defaultValue` attribute"
                )
            }
        }

        if (type == TYPE_BUNDLE || type == TYPE_BUNDLE_ARRAY) {
            if (element.hasAttributeNS(SdkConstants.ANDROID_URI, ATTR_DEFAULT_VALUE)) {
                context.report(
                    ISSUE,
                    element,
                    getAttrLocation(context, element, SdkConstants.ANDROID_URI, ATTR_DEFAULT_VALUE),
                    "Restriction type `$type` cannot have a default value"
                )
            }
        }

        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, ATTR_DEFAULT_VALUE)) {
            val defaultValue = element.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_DEFAULT_VALUE)
            if (type == TYPE_INTEGER) {
                try {
                    defaultValue.toInt()
                } catch (e: NumberFormatException) {
                    context.report(
                        ISSUE,
                        element,
                        getAttrLocation(context, element, SdkConstants.ANDROID_URI, ATTR_DEFAULT_VALUE),
                        "Invalid `android:defaultValue` value `$defaultValue` for type `integer`"
                    )
                }
            } else if (type == TYPE_BOOLEAN) {
                if (defaultValue != "true" && defaultValue != "false") {
                    context.report(
                        ISSUE,
                        element,
                        getAttrLocation(context, element, SdkConstants.ANDROID_URI, ATTR_DEFAULT_VALUE),
                        "Invalid `android:defaultValue` value `$defaultValue` for type `boolean`"
                    )
                }
            }
        }

        var hasChildren = false
        val nestedKeys = mutableSetOf<String>()
        var child = element.firstChild
        while (child != null) {
            if (child is Element) {
                val tagName = child.tagName
                if (tagName == TAG_RESTRICTION) {
                    if (type != TYPE_BUNDLE && type != TYPE_BUNDLE_ARRAY) {
                        context.report(
                            ISSUE,
                            child,
                            context.getLocation(child),
                            "Nested restrictions are only supported for `bundle` or `bundle_array` types"
                        )
                    } else {
                        validateRestriction(context, child, nestedKeys)
                    }
                } else {
                    context.report(
                        ISSUE,
                        child,
                        context.getLocation(child),
                        "Only `<restriction>` elements are allowed"
                    )
                }
                hasChildren = true
            }
            child = child.nextSibling
        }

        if ((type == TYPE_BUNDLE || type == TYPE_BUNDLE_ARRAY) && !hasChildren) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Restriction type `$type` must have at least one nested restriction"
            )
        }
    }
}
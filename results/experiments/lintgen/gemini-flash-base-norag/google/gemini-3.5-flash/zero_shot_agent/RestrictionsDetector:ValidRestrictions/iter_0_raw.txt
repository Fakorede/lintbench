package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_KEY
import com.android.SdkConstants.ATTR_TITLE
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

class RestrictionsDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = """
                Ensures that an application's restrictions XML file is properly formed.
                """,
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

        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
        private const val ATTR_ENTRIES = "entries"
        private const val ATTR_ENTRY_VALUES = "entryValues"

        private val VALID_TYPES = setOf(
            "bool",
            "integer",
            "string",
            "choice",
            "multi-select",
            "bundle",
            "bundle_array",
            "hidden"
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

        validateRestrictionsElement(context, root)
    }

    private fun validateRestrictionsElement(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                if (child.tagName != TAG_RESTRICTION) {
                    context.report(
                        ISSUE,
                        child,
                        context.getNameLocation(child),
                        "Only `<restriction>` elements are allowed under `<restrictions>`"
                    )
                } else {
                    validateRestriction(context, child)
                }
            }
        }
    }

    private fun validateRestriction(context: XmlContext, element: Element) {
        if (!element.hasAttributeNS(ANDROID_URI, ATTR_KEY)) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `android:key` attribute"
            )
        }

        if (!element.hasAttributeNS(ANDROID_URI, ATTR_TITLE)) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `android:title` attribute"
            )
        }

        val typeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
        if (typeAttr == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `android:restrictionType` attribute"
            )
            validateChildren(context, element, null)
            return
        }

        val type = typeAttr.value
        if (type !in VALID_TYPES) {
            context.report(
                ISSUE,
                typeAttr,
                context.getLocation(typeAttr),
                "Invalid `android:restrictionType` value: `$type`"
            )
            validateChildren(context, element, type)
            return
        }

        if (type == "choice" || type == "multi-select") {
            if (!element.hasAttributeNS(ANDROID_URI, ATTR_ENTRIES)) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Type `$type` requires `android:entries` attribute"
                )
            }
            if (!element.hasAttributeNS(ANDROID_URI, ATTR_ENTRY_VALUES)) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Type `$type` requires `android:entryValues` attribute"
                )
            }
        }

        validateChildren(context, element, type)
    }

    private fun validateChildren(context: XmlContext, element: Element, type: String?) {
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                if (type != "bundle" && type != "bundle_array") {
                    context.report(
                        ISSUE,
                        child,
                        context.getNameLocation(child),
                        "Nested restrictions are only supported for `bundle` and `bundle_array` types"
                    )
                } else if (child.tagName != TAG_RESTRICTION) {
                    context.report(
                        ISSUE,
                        child,
                        context.getNameLocation(child),
                        "Only `<restriction>` elements are allowed inside `<restriction>`"
                    )
                } else {
                    validateRestriction(context, child)
                }
            }
        }
    }
}
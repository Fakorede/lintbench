package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : Detector(), XmlScanner {

    companion object {
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"
        private const val ATTR_KEY = "key"
        private const val ATTR_TITLE = "title"
        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
        private const val ATTR_ENTRIES = "entries"
        private const val ATTR_ENTRY_VALUES = "entryValues"

        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = "Ensures that an applications restrictions XML file is properly formed according to the RestrictionsManager specification.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                RestrictionsDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_RESTRICTIONS, TAG_RESTRICTION)

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_RESTRICTIONS -> checkRestrictionsRoot(context, element)
            TAG_RESTRICTION -> checkRestrictionElement(context, element)
        }
    }

    private fun checkRestrictionsRoot(context: XmlContext, element: Element) {
        if (element.parentNode != null && element.parentNode.nodeType != Node.DOCUMENT_NODE) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "`<$TAG_RESTRICTIONS>` must be the root element"
            )
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName != TAG_RESTRICTION) {
                context.report(
                    ISSUE,
                    context.getLocation(child as Element),
                    "Invalid child element `<${child.nodeName}>`; only `<$TAG_RESTRICTION>` is allowed inside `<$TAG_RESTRICTIONS>`"
                )
            }
        }
    }

    private fun checkRestrictionElement(context: XmlContext, element: Element) {
        val androidNs = SdkConstants.ANDROID_URI

        checkRequiredAttribute(context, element, androidNs, ATTR_KEY)
        checkRequiredAttribute(context, element, androidNs, ATTR_TITLE)

        val typeAttr = element.getAttributeNodeNS(androidNs, ATTR_RESTRICTION_TYPE)
        if (typeAttr == null || typeAttr.value.isEmpty()) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "Missing required attribute `android:$ATTR_RESTRICTION_TYPE`"
            )
        } else {
            val type = typeAttr.value
            val validTypes = setOf("bool", "choice", "multi-select", "integer", "string", "hidden", "bundle", "bundle_array")
            if (type !in validTypes) {
                context.report(
                    ISSUE,
                    context.getLocation(typeAttr),
                    "Invalid restriction type `$type`. Must be one of: ${validTypes.joinToString(", ")}"
                )
            } else if (type == "choice" || type == "multi-select") {
                checkRequiredAttribute(context, element, androidNs, ATTR_ENTRIES)
                checkRequiredAttribute(context, element, androidNs, ATTR_ENTRY_VALUES)
            }
        }
    }

    private fun checkRequiredAttribute(context: XmlContext, element: Element, namespace: String, attrName: String) {
        val attr = element.getAttributeNodeNS(namespace, attrName)
        if (attr == null || attr.value.isEmpty()) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "Missing required attribute `android:$attrName`"
            )
        }
    }
}
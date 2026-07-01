package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : Detector(), XmlScanner {

    companion object {
        const val TAG_RESTRICTIONS = "restrictions"
        const val TAG_RESTRICTION = "restriction"
        const val ATTR_KEY = "key"
        const val ATTR_TITLE = "title"
        const val ATTR_RESTRICTION_TYPE = "restrictionType"
        const val ATTR_ENTRIES = "entries"
        const val ATTR_ENTRY_VALUES = "entryValues"
        const val ATTR_DEFAULT_VALUE = "defaultValue"

        val VALID_TYPES = setOf("bool", "choice", "multi-select", "integer", "string", "hidden", "bundle", "bundle_array")

        @JvmField
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

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_RESTRICTIONS)

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.parentNode != null && element.parentNode.nodeType != Node.DOCUMENT_NODE) {
            context.report(ISSUE, context.getLocation(element), "`<$TAG_RESTRICTIONS>` must be the root element")
            return
        }

        val keys = mutableSetOf<String>()
        checkChildren(context, element, keys, 0)
    }

    private fun checkChildren(context: XmlContext, parent: Element, keys: MutableSet<String>, depth: Int) {
        val children = parent.childNodes
        var childCount = 0
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                if (child.nodeName != TAG_RESTRICTION) {
                    context.report(ISSUE, context.getLocation(child as Element), "Invalid child element `<${child.nodeName}>`; only `<$TAG_RESTRICTION>` is allowed")
                } else {
                    childCount++
                    val restriction = child as Element
                    checkRestriction(context, restriction, keys, depth)
                    checkChildren(context, restriction, keys, depth + 1)
                }
            }
        }
        if (childCount > 100) {
            context.report(ISSUE, context.getLocation(parent), "Too many child restrictions")
        }
    }

    private fun checkRestriction(context: XmlContext, element: Element, keys: MutableSet<String>, depth: Int) {
        if (depth > 2) {
            context.report(ISSUE, context.getLocation(element), "Nesting depth exceeds maximum allowed depth of 2")
        }

        val androidNs = SdkConstants.ANDROID_URI

        val keyAttr = element.getAttributeNodeNS(androidNs, ATTR_KEY)
        if (keyAttr == null || keyAttr.value.isEmpty()) {
            context.report(ISSUE, context.getLocation(element), "Missing required attribute `android:$ATTR_KEY`")
        } else {
            val key = keyAttr.value
            if (key.startsWith("@")) {
                context.report(ISSUE, context.getLocation(keyAttr), "`android:$ATTR_KEY` should not be localized")
            }
            if (!keys.add(key)) {
                context.report(ISSUE, context.getLocation(keyAttr), "Duplicate key `$key`")
            }
        }

        val titleAttr = element.getAttributeNodeNS(androidNs, ATTR_TITLE)
        if (titleAttr == null || titleAttr.value.isEmpty()) {
            context.report(ISSUE, context.getLocation(element), "Missing required attribute `android:$ATTR_TITLE`")
        }

        val typeAttr = element.getAttributeNodeNS(androidNs, ATTR_RESTRICTION_TYPE)
        if (typeAttr == null || typeAttr.value.isEmpty()) {
            context.report(ISSUE, context.getLocation(element), "Missing required attribute `android:$ATTR_RESTRICTION_TYPE`")
            return
        }

        val type = typeAttr.value
        if (type !in VALID_TYPES) {
            context.report(ISSUE, context.getLocation(typeAttr), "Invalid restriction type `$type`. Must be one of: ${VALID_TYPES.joinToString(", ")}")
            return
        }

        if (type == "choice" || type == "multi-select") {
            val entriesAttr = element.getAttributeNodeNS(androidNs, ATTR_ENTRIES)
            val entryValuesAttr = element.getAttributeNodeNS(androidNs, ATTR_ENTRY_VALUES)
            if (entriesAttr == null || entriesAttr.value.isEmpty()) {
                context.report(ISSUE, context.getLocation(element), "Missing required attribute `android:$ATTR_ENTRIES` for type `$type`")
            }
            if (entryValuesAttr == null || entryValuesAttr.value.isEmpty()) {
                context.report(ISSUE, context.getLocation(element), "Missing required attribute `android:$ATTR_ENTRY_VALUES` for type `$type`")
            }
        }

        if (type == "bundle" || type == "bundle_array") {
            val defaultAttr = element.getAttributeNodeNS(androidNs, ATTR_DEFAULT_VALUE)
            if (defaultAttr != null) {
                context.report(ISSUE, context.getLocation(defaultAttr), "`android:$ATTR_DEFAULT_VALUE` is not allowed for type `$type`")
            }
        }

        if (type == "integer") {
            val defaultAttr = element.getAttributeNodeNS(androidNs, ATTR_DEFAULT_VALUE)
            if (defaultAttr != null && defaultAttr.value.isNotEmpty()) {
                try {
                    defaultAttr.value.toInt()
                } catch (e: NumberFormatException) {
                    context.report(ISSUE, context.getLocation(defaultAttr), "Invalid integer value `${defaultAttr.value}` for `android:$ATTR_DEFAULT_VALUE`")
                }
            }
        }
    }
}
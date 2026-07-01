package com.android.tools.lint.checks

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

        const val MAX_NESTING_DEPTH = 2
        const val MAX_NUMBER_OF_NESTED_RESTRICTIONS = 100

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
                Scope.XML_RESOURCE_SCOPE
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
        if (childCount > MAX_NUMBER_OF_NESTED_RESTRICTIONS) {
            context.report(ISSUE, context.getLocation(parent), "Too many child restrictions")
        }
    }

    private fun checkRestriction(context: XmlContext, element: Element, keys: MutableSet<String>, depth: Int) {
        if (depth > MAX_NESTING_DEPTH) {
            context.report(ISSUE, context.getLocation(element), "Nesting depth exceeds maximum allowed depth of $MAX_NESTING_DEPTH")
        }

        val key = element.getAttribute("android:$ATTR_KEY")
        if (key.isEmpty()) {
            context.report(ISSUE, context.getLocation(element), "Missing required attribute `android:$ATTR_KEY`")
        } else {
            if (key.startsWith("@")) {
                context.report(ISSUE, context.getLocation(element), "`android:$ATTR_KEY` should not be localized")
            }
            if (!keys.add(key)) {
                context.report(ISSUE, context.getLocation(element), "Duplicate key `$key`")
            }
        }

        val type = element.getAttribute("android:$ATTR_RESTRICTION_TYPE")
        if (type.isEmpty()) {
            context.report(ISSUE, context.getLocation(element), "Missing required attribute `android:$ATTR_RESTRICTION_TYPE`")
            return
        }

        if (type !in VALID_TYPES) {
            context.report(ISSUE, context.getLocation(element), "Invalid restriction type `$type`. Must be one of: ${VALID_TYPES.joinToString(", ")}")
            return
        }

        if (type != "hidden") {
            val title = element.getAttribute("android:$ATTR_TITLE")
            if (title.isEmpty()) {
                context.report(ISSUE, context.getLocation(element), "Missing required attribute `android:$ATTR_TITLE`")
            }
        }

        if (type == "choice" || type == "multi-select") {
            val entries = element.getAttribute("android:$ATTR_ENTRIES")
            val entryValues = element.getAttribute("android:$ATTR_ENTRY_VALUES")
            if (entries.isEmpty()) {
                context.report(ISSUE, context.getLocation(element), "Missing required attribute `android:$ATTR_ENTRIES` for type `$type`")
            }
            if (entryValues.isEmpty()) {
                context.report(ISSUE, context.getLocation(element), "Missing required attribute `android:$ATTR_ENTRY_VALUES` for type `$type`")
            }
        }

        if (type == "bundle" || type == "bundle_array") {
            val defaultVal = element.getAttribute("android:$ATTR_DEFAULT_VALUE")
            if (defaultVal.isNotEmpty()) {
                context.report(ISSUE, context.getLocation(element), "`android:$ATTR_DEFAULT_VALUE` is not allowed for type `$type`")
            }
            var hasChildren = false
            val childNodes = element.childNodes
            for (i in 0 until childNodes.length) {
                if (childNodes.item(i).nodeType == Node.ELEMENT_NODE) {
                    hasChildren = true
                    break
                }
            }
            if (hasChildren) {
                context.report(ISSUE, context.getLocation(element), "Type `$type` cannot have child restrictions")
            }
        }

        if (type == "integer") {
            val defaultVal = element.getAttribute("android:$ATTR_DEFAULT_VALUE")
            if (defaultVal.isNotEmpty()) {
                try {
                    defaultVal.toInt()
                } catch (e: NumberFormatException) {
                    context.report(ISSUE, context.getLocation(element), "Invalid integer value `$defaultVal` for `android:$ATTR_DEFAULT_VALUE`")
                }
            }
        }
    }
}
package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : Detector(), XmlScanner {
    private val seenKeys = mutableSetOf<String>()

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        const val MAX_NUMBER_OF_NESTED_RESTRICTIONS = 2
        const val MAX_CHILDREN = 100

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = "Ensures that an applications restrictions XML file is properly formed.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                RestrictionsDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val VALID_TYPES = setOf(
            "bool", "choice", "multi-select", "integer", "string", "hidden", "bundle", "bundle_array"
        )
    }

    override fun beforeCheckFile(context: Context) {
        seenKeys.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun getApplicableElements(): Collection<String>? = listOf("restrictions", "restriction")

    override fun visitElement(context: XmlContext, element: Element) {
        val tag = element.tagName
        if (tag != "restriction") return

        val parent = element.parentNode
        val keyAttr = element.getAttributeNodeNS(ANDROID_URI, "key")
        val titleAttr = element.getAttributeNodeNS(ANDROID_URI, "title")
        val typeAttr = element.getAttributeNodeNS(ANDROID_URI, "restrictionType")
        val defaultAttr = element.getAttributeNodeNS(ANDROID_URI, "defaultValue")
        val entriesAttr = element.getAttributeNodeNS(ANDROID_URI, "entries")
        val entryValuesAttr = element.getAttributeNodeNS(ANDROID_URI, "entryValues")

        val key = keyAttr?.value ?: ""
        val title = titleAttr?.value ?: ""
        val type = typeAttr?.value ?: ""
        val defaultValue = defaultAttr?.value ?: ""

        if (key.isEmpty()) {
            context.report(ISSUE, context.getLocation(keyAttr ?: element), "Missing required attribute `android:key`")
        } else {
            if (key.startsWith("@")) {
                context.report(ISSUE, context.getLocation(keyAttr ?: element), "Attribute `android:key` cannot be a resource reference")
            }
            if (!seenKeys.add(key)) {
                context.report(ISSUE, context.getLocation(keyAttr ?: element), "Duplicate key `$key`")
            }
        }

        if (title.isEmpty() && type != "hidden") {
            context.report(ISSUE, context.getLocation(titleAttr ?: element), "Missing required attribute `android:title`")
        }

        if (type.isEmpty()) {
            context.report(ISSUE, context.getLocation(typeAttr ?: element), "Missing required attribute `android:restrictionType`")
            return
        }
        if (type !in VALID_TYPES) {
            context.report(ISSUE, context.getLocation(typeAttr ?: element), "Invalid restriction type `$type`")
            return
        }

        when (type) {
            "choice", "multi-select" -> {
                if (entriesAttr == null) {
                    context.report(ISSUE, context.getLocation(element), "Missing required attribute `android:entries` for type `$type`")
                }
                if (entryValuesAttr == null) {
                    context.report(ISSUE, context.getLocation(element), "Missing required attribute `android:entryValues` for type `$type`")
                }
            }
            "bundle", "bundle_array" -> {
                if (defaultAttr != null) {
                    context.report(ISSUE, context.getLocation(defaultAttr), "Attribute `android:defaultValue` is not allowed for type `$type`")
                }
            }
            "integer" -> {
                if (defaultValue.isNotEmpty() && defaultValue.toIntOrNull() == null) {
                    context.report(ISSUE, context.getLocation(defaultAttr ?: element), "Invalid integer value `$defaultValue`")
                }
            }
            "bool" -> {
                if (defaultValue.isNotEmpty() && defaultValue != "true" && defaultValue != "false") {
                    context.report(ISSUE, context.getLocation(defaultAttr ?: element), "Invalid boolean value `$defaultValue`")
                }
            }
        }

        val childNodes = element.childNodes
        val childElements = mutableListOf<Element>()
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                childElements.add(child as Element)
            }
        }

        if (type == "bundle_array" && childElements.isNotEmpty()) {
            context.report(ISSUE, context.getLocation(element), "Type `bundle_array` cannot have children")
        }

        if (childElements.size > MAX_CHILDREN) {
            context.report(ISSUE, context.getLocation(element), "Too many children (${childElements.size}), maximum is $MAX_CHILDREN")
        }

        var depth = 0
        var p: Node? = parent
        while (p != null) {
            if (p is Element && p.tagName == "restriction") depth++
            p = p.parentNode
        }
        if (depth > MAX_NUMBER_OF_NESTED_RESTRICTIONS) {
            context.report(ISSUE, context.getLocation(element), "Nesting depth exceeds maximum of $MAX_NUMBER_OF_NESTED_RESTRICTIONS")
        }
    }
}
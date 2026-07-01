package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : ResourceXmlDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        if (context.file.name != APP_RESTRICTIONS_FILE) {
            return
        }
        val root = document.documentElement ?: return
        if (root.tagName != TAG_RESTRICTIONS) {
            report(context, root, "Restrictions file must have a `<restrictions>` root element")
        }
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(TAG_RESTRICTIONS, TAG_RESTRICTION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.file.name != APP_RESTRICTIONS_FILE) {
            return
        }
        when (element.tagName) {
            TAG_RESTRICTIONS -> checkRestrictionsRoot(context, element)
            TAG_RESTRICTION -> checkRestriction(context, element)
        }
    }

    private fun checkRestrictionsRoot(context: XmlContext, element: Element) {
        val parent = element.parentNode
        if (parent != null && parent.nodeType == Node.ELEMENT_NODE) {
            report(context, element, "`<restrictions>` must be the root element")
        }
        for (child in element.childElements()) {
            if (child.tagName != TAG_RESTRICTION) {
                report(context, child, "`<restrictions>` can only contain `<restriction>` elements")
            }
        }
    }

    private fun checkRestriction(context: XmlContext, element: Element) {
        val key = element.getAttribute(ATTR_KEY)
        if (key.isBlank()) {
            report(context, element, "A `<restriction>` must specify a `key` attribute")
        }

        val restrictionType = element.getAttribute(ATTR_RESTRICTION_TYPE)
        if (restrictionType.isBlank()) {
            report(context, element, "A `<restriction>` must specify a `restrictionType` attribute")
            return
        }

        if (restrictionType !in VALID_TYPES) {
            report(context, element, "Invalid restriction type `$restrictionType`")
            return
        }

        val title = element.getAttribute(ATTR_TITLE)
        if (title.isBlank() && restrictionType != TYPE_HIDDEN) {
            report(context, element, "A `<restriction>` must specify a `title` attribute")
        }

        when (restrictionType) {
            TYPE_BUNDLE, TYPE_BUNDLE_ARRAY -> {
                val childRestrictions = element.childElements().filter { it.tagName == TAG_RESTRICTION }
                if (childRestrictions.isEmpty()) {
                    report(context, element, "A `$restrictionType` restriction must contain nested `<restriction>` elements")
                }
            }
            TYPE_CHOICE, TYPE_MULTI_SELECT -> {
                val entries = element.getAttribute(ATTR_ENTRIES)
                val entryValues = element.getAttribute(ATTR_ENTRY_VALUES)
                if (entries.isBlank()) {
                    report(context, element, "A `$restrictionType` restriction must specify an `entries` attribute")
                }
                if (entryValues.isBlank()) {
                    report(context, element, "A `$restrictionType` restriction must specify an `entryValues` attribute")
                }
            }
            TYPE_INTEGER -> {
                val defaultValue = element.getAttribute(ATTR_DEFAULT_VALUE)
                if (defaultValue.isNotBlank() && defaultValue.toIntOrNull() == null) {
                    report(context, element, "Default value for an `integer` restriction must be an integer")
                }
            }
        }
    }

    private fun Element.childElements(): List<Element> {
        val result = mutableListOf<Element>()
        var child = firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                result.add(child as Element)
            }
            child = child.nextSibling
        }
        return result
    }

    private fun report(context: XmlContext, node: Node, message: String) {
        context.report(ISSUE, node, context.getLocation(node), message)
    }

    companion object {
        private const val APP_RESTRICTIONS_FILE = "app_restrictions.xml"
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"
        private const val ATTR_KEY = "key"
        private const val ATTR_TITLE = "title"
        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
        private const val ATTR_DEFAULT_VALUE = "defaultValue"
        private const val ATTR_ENTRIES = "entries"
        private const val ATTR_ENTRY_VALUES = "entryValues"
        private const val TYPE_BUNDLE = "bundle"
        private const val TYPE_BUNDLE_ARRAY = "bundle_array"
        private const val TYPE_CHOICE = "choice"
        private const val TYPE_MULTI_SELECT = "multi-select"
        private const val TYPE_HIDDEN = "hidden"
        private const val TYPE_INTEGER = "integer"

        private val VALID_TYPES = setOf(
            TYPE_BUNDLE,
            TYPE_BUNDLE_ARRAY,
            TYPE_CHOICE,
            TYPE_MULTI_SELECT,
            TYPE_HIDDEN,
            TYPE_INTEGER,
            "string"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = """
                Ensures that an application's restrictions XML file is properly formed.
                
                The `res/xml/app_restrictions.xml` file describes the restrictions that can \
                be applied to the application by a restricted profile owner. Each \
                `<restriction>` must define a `key` and a valid `restrictionType`, and \
                certain types (such as `choice`, `multi-select`, `bundle`, and \
                `bundle_array`) require additional attributes or nested elements.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                RestrictionsDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
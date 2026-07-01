package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : ResourceXmlDetector() {

    companion object {
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"
        private const val ATTR_KEY = "key"
        private const val ATTR_TYPE = "restrictionType"
        private const val ATTR_TITLE = "title"
        private const val ATTR_DEFAULT_VALUE = "defaultValue"
        private const val ATTR_ENTRIES = "entries"
        private const val ATTR_ENTRY_VALUES = "entryValues"

        private val RESTRICTION_TYPES = setOf(
            "bundle",
            "bundle_array",
            "choice",
            "multiSelect",
            "hidden",
            "bool",
            "string",
            "integer",
        )

        private val IMPLEMENTATION = Implementation(
            RestrictionsDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = "A managed configurations / restrictions XML file must have a " +
                "\"<restrictions>\" root tag, each \"<restriction>\" must define a non-empty " +
                "\"key\" and a recognized \"restrictionType\", and type-specific rules " +
                "(e.g. choice/multiSelect require entries and entryValues, bundles must " +
                "contain only nested restrictions) must be followed.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.XML

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != TAG_RESTRICTIONS && root.localName != TAG_RESTRICTIONS) {
            return
        }

        for (child in root.childElements()) {
            if (child.tagName != TAG_RESTRICTION && child.localName != TAG_RESTRICTION) {
                reportError(
                    context,
                    child,
                    "A restrictions file must contain only <restriction> elements",
                )
            } else {
                validateRestriction(context, child)
            }
        }
    }

    private fun validateRestriction(context: XmlContext, restriction: Element) {
        val key = getAttributeValue(restriction, ATTR_KEY)
        if (key.isNullOrBlank()) {
            reportError(
                context,
                restriction,
                "A <restriction> element must specify a non-empty \"key\" attribute",
            )
        }

        val type = getAttributeValue(restriction, ATTR_TYPE)
        if (type.isNullOrBlank()) {
            reportError(
                context,
                restriction,
                "A <restriction> element must specify a \"restrictionType\" attribute",
            )
            return
        }

        if (type !in RESTRICTION_TYPES) {
            reportError(
                context,
                restriction,
                "Unknown restrictionType \"$type\"; must be one of $RESTRICTION_TYPES",
            )
            return
        }

        when (type) {
            "bundle", "bundle_array" -> {
                for (child in restriction.childElements()) {
                    if (child.tagName != TAG_RESTRICTION && child.localName != TAG_RESTRICTION) {
                        reportError(
                            context,
                            child,
                            "A $type restriction must contain only nested <restriction> elements",
                        )
                    } else {
                        validateRestriction(context, child)
                    }
                }
            }
            "choice" -> {
                if (!hasAttribute(restriction, ATTR_ENTRIES) ||
                    !hasAttribute(restriction, ATTR_ENTRY_VALUES)
                ) {
                    reportError(
                        context,
                        restriction,
                        "A choice restriction must specify both \"entries\" and \"entryValues\" attributes",
                    )
                }
            }
            "multiSelect" -> {
                if (!hasAttribute(restriction, ATTR_ENTRIES) ||
                    !hasAttribute(restriction, ATTR_ENTRY_VALUES)
                ) {
                    reportError(
                        context,
                        restriction,
                        "A multiSelect restriction must specify both \"entries\" and \"entryValues\" attributes",
                    )
                }
            }
            "hidden" -> {
                if (!hasAttribute(restriction, ATTR_DEFAULT_VALUE)) {
                    reportError(
                        context,
                        restriction,
                        "A hidden restriction must specify a \"defaultValue\" attribute",
                    )
                }
            }
            else -> {
                // bool, string, integer: no additional mandatory attributes
            }
        }
    }

    private fun reportError(context: XmlContext, node: Node, message: String) {
        context.report(ISSUE, context.getLocation(node), message)
    }

    private fun getAttributeValue(element: Element, name: String): String? {
        if (element.hasAttribute(name)) {
            return element.getAttribute(name).takeIf { it.isNotBlank() }
        }
        val prefixed = "android:$name"
        if (element.hasAttribute(prefixed)) {
            return element.getAttribute(prefixed).takeIf { it.isNotBlank() }
        }

        // Also check by local name in case a different prefix is used.
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i) as? Attr ?: continue
            if (name == attr.localName && attr.value.isNotBlank()) {
                return attr.value
            }
        }

        return null
    }

    private fun hasAttribute(element: Element, name: String): Boolean =
        getAttributeValue(element, name) != null

    private fun Node.childElements(): List<Element> {
        val result = mutableListOf<Element>()
        val children = this.childNodes ?: return result
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child != null && child.nodeType == Node.ELEMENT_NODE) {
                result.add(child as Element)
            }
        }
        return result
    }
}
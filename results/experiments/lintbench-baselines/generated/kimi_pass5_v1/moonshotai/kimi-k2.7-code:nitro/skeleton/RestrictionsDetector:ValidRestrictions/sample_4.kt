package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : ResourceXmlDetector() {

    companion object {
        private const val RESTRICTIONS_TAG = "restrictions"
        private const val RESTRICTION_TAG = "restriction"
        private const val ENTRY_TAG = "entry"
        private const val ENTRY_VALUE_TAG = "entryValue"

        private val SUPPORTED_TYPES = setOf(
            "bool",
            "string",
            "integer",
            "choice",
            "multi-select",
            "hidden",
            "bundle",
            "bundle_array"
        )

        private val CONTAINER_TYPES = setOf("bundle", "bundle_array")
        private val CHOICE_TYPES = setOf("choice", "multi-select")

        private val IMPLEMENTATION = Implementation(
            RestrictionsDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = """
                Managed configuration restrictions are defined in an XML resource file
                with a `<restrictions>` root element. Each `<restriction>` must declare a
                valid `android:key` and a supported `android:restrictionType`. Choice and
                multi-select restrictions must contain matching `<entry>` and
                `<entryValue>` elements, and nested `<restriction>` elements are only
                allowed inside types '${CONTAINER_TYPES.joinToString()}'.
            """,
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
        if (root.localName != RESTRICTIONS_TAG) {
            return
        }

        val keys = mutableSetOf<String>()
        for (child in root.childElements()) {
            if (child.localName == RESTRICTION_TAG) {
                validateRestriction(context, child, keys)
            }
        }
    }

    private fun validateRestriction(
        context: XmlContext,
        element: Element,
        keys: MutableSet<String>
    ) {
        val key = element.getAndroidAttributeValue("key")
        val restrictionType = element.getAndroidAttributeValue("restrictionType")

        if (key.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Restriction is missing required android:key attribute"
            )
        } else if (!keys.add(key)) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Duplicate restriction key: $key"
            )
        }

        if (restrictionType.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Restriction '${key ?: "<unknown>"}' is missing required android:restrictionType attribute"
            )
            return
        }

        if (restrictionType !in SUPPORTED_TYPES) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Unknown restriction type '$restrictionType'"
            )
            return
        }

        if (restrictionType in CONTAINER_TYPES) {
            var foundChild = false
            val childKeys = mutableSetOf<String>()
            for (child in element.childElements()) {
                if (child.localName == RESTRICTION_TAG) {
                    foundChild = true
                    validateRestriction(context, child, childKeys)
                }
            }
            if (!foundChild) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Restriction of type '$restrictionType' must contain at least one nested <restriction>"
                )
            }
        } else {
            for (child in element.childElements()) {
                if (child.localName == RESTRICTION_TAG) {
                    context.report(
                        ISSUE,
                        child,
                        context.getLocation(child),
                        "Nested <restriction> elements are only allowed inside types ${CONTAINER_TYPES.joinToString()}"
                    )
                }
            }

            if (restrictionType in CHOICE_TYPES) {
                validateChoiceRestriction(context, element)
            }
        }
    }

    private fun validateChoiceRestriction(context: XmlContext, element: Element) {
        val restrictionType = element.getAndroidAttributeValue("restrictionType") ?: "choice"
        val entries = element.childElements().filter { it.localName == ENTRY_TAG }.toList()
        val entryValues = element.childElements().filter { it.localName == ENTRY_VALUE_TAG }.toList()

        if (entries.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "A '$restrictionType' restriction must contain <entry> elements"
            )
        }
        if (entryValues.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "A '$restrictionType' restriction must contain <entryValue> elements"
            )
        }
        if (entries.size != entryValues.size) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "A '$restrictionType' restriction must contain equal numbers of <entry> and <entryValue> elements (found ${entries.size} and ${entryValues.size})"
            )
        }
    }

    private fun Node.childElements(): Sequence<Element> {
        val nodes = childNodes ?: return emptySequence()
        return (0 until nodes.length).asSequence().mapNotNull { nodes.item(it) as? Element }
    }

    private fun Element.getAndroidAttributeValue(name: String): String? {
        val attributes = this.attributes ?: return null
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            if (attr.localName == name) {
                val value = attr.value
                if (value.isNotEmpty()) {
                    return value
                }
            }
        }
        return null
    }
}
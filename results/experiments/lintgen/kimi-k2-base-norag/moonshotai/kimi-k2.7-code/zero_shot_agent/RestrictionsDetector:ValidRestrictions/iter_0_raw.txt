package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.Locale

class RestrictionsDetector : ResourceXmlDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.XML

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_RESTRICTIONS)

    override fun visitElement(context: XmlContext, element: Element) {
        checkRestrictionsElement(context, element)
    }

    private fun checkRestrictionsElement(context: XmlContext, element: Element) {
        val children = element.childElements()
        if (children.isEmpty()) {
            report(context, element, "Restrictions descriptor must contain at least one <restriction>")
        }

        val keys = mutableMapOf<String, Element>()
        for (child in children) {
            if (child.localName != TAG_RESTRICTION) {
                report(context, child, "Only <restriction> elements are allowed inside <restrictions>")
                continue
            }
            checkRestrictionElement(context, child, keys)
        }
    }

    private fun checkRestrictionElement(
        context: XmlContext,
        element: Element,
        keys: MutableMap<String, Element>
    ) {
        val key = getAttribute(element, ATTR_KEY)
        if (key.isNullOrBlank()) {
            report(context, element, "Restriction must specify a 'key' attribute")
        } else {
            if (keys.put(key, element) != null) {
                report(context, element, "Duplicate restriction key '$key'")
            }
        }

        if (getAttribute(element, ATTR_TITLE).isNullOrBlank()) {
            report(context, element, "Restriction must specify a 'title' attribute")
        }

        val type = getAttribute(element, ATTR_RESTRICTION_TYPE)?.lowercase(Locale.US)
        if (type.isNullOrBlank()) {
            report(context, element, "Restriction must specify a 'restrictionType' attribute")
            return
        }

        if (type !in VALID_TYPES) {
            report(context, element, "Unknown restriction type '$type'")
            return
        }

        val children = element.childElements()
        when (type) {
            TYPE_CHOICE -> {
                if (children.isEmpty()) {
                    report(context, element, "A restriction of type 'choice' must contain <choice> elements")
                }
                for (child in children) {
                    if (child.localName != TAG_CHOICE) {
                        report(context, child, "Only <choice> elements are allowed inside a 'choice' restriction")
                    } else {
                        if (getAttribute(child, ATTR_VALUE).isNullOrBlank()) {
                            report(context, child, "<choice> must specify a 'value' attribute")
                        }
                        if (getAttribute(child, ATTR_TITLE).isNullOrBlank()) {
                            report(context, child, "<choice> must specify a 'title' attribute")
                        }
                    }
                }
            }
            TYPE_BUNDLE -> {
                if (children.isEmpty()) {
                    report(context, element, "A restriction of type 'bundle' must contain nested <restriction> elements")
                }
                val nestedKeys = mutableMapOf<String, Element>()
                for (child in children) {
                    if (child.localName != TAG_RESTRICTION) {
                        report(context, child, "A 'bundle' restriction can only contain nested <restriction> elements")
                    } else {
                        checkRestrictionElement(context, child, nestedKeys)
                    }
                }
            }
            TYPE_BUNDLE_ARRAY -> {
                if (children.isEmpty()) {
                    report(context, element, "A restriction of type 'bundle_array' must contain nested <restriction> elements")
                }
                for (child in children) {
                    if (child.localName != TAG_RESTRICTION) {
                        report(context, child, "A 'bundle_array' restriction can only contain nested <restriction> elements")
                    } else {
                        val childType = getAttribute(child, ATTR_RESTRICTION_TYPE)?.lowercase(Locale.US)
                        if (childType != TYPE_BUNDLE) {
                            report(context, child, "Nested restrictions inside a 'bundle_array' must be of type 'bundle'")
                        } else {
                            val nestedKeys = mutableMapOf<String, Element>()
                            checkRestrictionElement(context, child, nestedKeys)
                        }
                    }
                }
            }
            else -> {
                if (children.isNotEmpty()) {
                    report(context, element, "Restrictions of type '$type' cannot contain child elements")
                }
            }
        }
    }

    private fun report(context: XmlContext, element: Element, message: String) {
        context.report(ISSUE, element, context.getLocation(element), message)
    }

    private fun getAttribute(element: Element, name: String): String? {
        val value = element.getAttributeNS(ANDROID_URI, name)
        if (!value.isNullOrEmpty()) {
            return value
        }
        return element.getAttribute(name).takeIf { it.isNotEmpty() }
    }

    private fun Node.childElements(): List<Element> =
        (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

    companion object {
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"
        private const val TAG_CHOICE = "choice"

        private const val ATTR_KEY = "key"
        private const val ATTR_TITLE = "title"
        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
        private const val ATTR_VALUE = "value"

        private const val TYPE_BOOL = "bool"
        private const val TYPE_STRING = "string"
        private const val TYPE_INTEGER = "integer"
        private const val TYPE_CHOICE = "choice"
        private const val TYPE_HIDDEN = "hidden"
        private const val TYPE_BUNDLE = "bundle"
        private const val TYPE_BUNDLE_ARRAY = "bundle_array"

        private val VALID_TYPES = setOf(
            TYPE_BOOL,
            TYPE_STRING,
            TYPE_INTEGER,
            TYPE_CHOICE,
            TYPE_HIDDEN,
            TYPE_BUNDLE,
            TYPE_BUNDLE_ARRAY
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            "ValidRestrictions",
            "Invalid Restrictions Descriptor",
            """
                Restrictions XML resources (used by android.content.RestrictionsManager) must follow
                the documented schema. Each <restriction> must have a key, title, and valid
                restrictionType. Types such as choice, bundle, and bundle_array require the
                correct child elements.
            """.trimIndent(),
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            Implementation(
                RestrictionsDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
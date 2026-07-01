package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.ResourceXmlDetector
import org.w3c.dom.Document
import org.w3c.dom.Element

class RestrictionsDetector : ResourceXmlDetector() {

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val ATTR_KEY = "key"
        private const val ATTR_TITLE = "title"
        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
        private const val ATTR_ENTRIES = "entries"
        private const val ATTR_ENTRY_VALUES = "entryValues"
        private const val ATTR_DEFAULT_VALUE = "defaultValue"

        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"

        private val IMPLEMENTATION = Implementation(
            RestrictionsDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = """
                Ensures that an application's restrictions XML file is properly formed.
                
                The XML file must have `<restrictions>` as the root element.
                Each restriction must specify a valid key and restrictionType.
                Depending on the restrictionType, other attributes like title, entries, and entryValues may be required or disallowed.
                Nested restrictions are only allowed for `bundle` and `bundle_array` types.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val rootTagName = root.localName ?: root.tagName
        if (rootTagName != TAG_RESTRICTIONS) {
            return
        }
        validateRestrictionsElement(context, root)
    }

    private fun validateRestrictionsElement(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        val keys = mutableSetOf<String>()
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                val tagName = child.localName ?: child.tagName
                if (tagName != TAG_RESTRICTION) {
                    context.report(
                        ISSUE,
                        child,
                        context.getNameLocation(child),
                        "Only <restriction> elements are allowed here"
                    )
                    continue
                }
                validateRestriction(context, child, keys)
            }
        }
    }

    private fun validateRestriction(context: XmlContext, element: Element, parentKeys: MutableSet<String>) {
        val key = element.getAndroidAttribute(ATTR_KEY)
        if (key.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing 'key' attribute"
            )
        } else {
            if (!parentKeys.add(key)) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Duplicate restriction key \"$key\""
                )
            }
        }

        val type = element.getAndroidAttribute(ATTR_RESTRICTION_TYPE)
        if (type.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing 'restrictionType' attribute"
            )
            return
        }

        val validTypes = setOf("string", "bool", "integer", "choice", "multi-select", "hidden", "bundle", "bundle_array")
        if (type !in validTypes) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Invalid 'restrictionType' \"$type\""
            )
            return
        }

        val title = element.getAndroidAttribute(ATTR_TITLE)
        if (title.isNullOrEmpty() && type != "hidden" && type != "bundle" && type != "bundle_array") {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Restrictions of type \"$type\" require a 'title' attribute"
            )
        }

        if (type == "choice" || type == "multi-select") {
            val entries = element.getAndroidAttribute(ATTR_ENTRIES)
            val entryValues = element.getAndroidAttribute(ATTR_ENTRY_VALUES)
            if (entries.isNullOrEmpty() || entryValues.isNullOrEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Restrictions of type \"$type\" require both 'entries' and 'entryValues' attributes"
                )
            }
        }

        if (type == "bundle" || type == "bundle_array") {
            if (element.getAndroidAttribute(ATTR_DEFAULT_VALUE) != null) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Restrictions of type \"$type\" cannot have a 'defaultValue'"
                )
            }
        }

        val childNodes = element.childNodes
        val childKeys = mutableSetOf<String>()
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                val childTagName = child.localName ?: child.tagName
                if (type != "bundle" && type != "bundle_array") {
                    context.report(
                        ISSUE,
                        child,
                        context.getNameLocation(child),
                        "Restrictions of type \"$type\" cannot have nested restrictions"
                    )
                    continue
                }

                if (childTagName != TAG_RESTRICTION) {
                    context.report(
                        ISSUE,
                        child,
                        context.getNameLocation(child),
                        "Only <restriction> elements are allowed here"
                    )
                    continue
                }

                if (type == "bundle_array") {
                    val childType = child.getAndroidAttribute(ATTR_RESTRICTION_TYPE)
                    if (childType != "bundle") {
                        context.report(
                            ISSUE,
                            child,
                            context.getNameLocation(child),
                            "Nested restrictions in a \"bundle_array\" must be of type \"bundle\""
                        )
                    }
                }

                validateRestriction(context, child, childKeys)
            }
        }
    }

    private fun Element.getAndroidAttribute(localName: String): String? {
        return if (hasAttributeNS(ANDROID_URI, localName)) {
            getAttributeNS(ANDROID_URI, localName)
        } else if (hasAttribute("android:$localName")) {
            getAttribute("android:$localName")
        } else {
            null
        }
    }
}
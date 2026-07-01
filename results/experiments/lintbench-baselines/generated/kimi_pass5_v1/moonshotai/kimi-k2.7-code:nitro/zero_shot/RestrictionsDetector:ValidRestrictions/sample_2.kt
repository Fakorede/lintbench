package com.android.tools.lint.checks

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

class RestrictionsDetector : ResourceXmlDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.XML

    override fun getApplicableElements(): Collection<String> =
        listOf(TAG_RESTRICTIONS, TAG_RESTRICTION)

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_RESTRICTIONS -> checkRestrictionsRoot(context, element)
            TAG_RESTRICTION -> checkRestriction(context, element)
        }
    }

    private fun checkRestrictionsRoot(context: XmlContext, element: Element) {
        if (element.parentNode?.nodeType != Node.DOCUMENT_NODE) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "The <restrictions> element must be the root element"
            )
        }

        if (context.file.name != APP_RESTRICTIONS_FILE) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Restrictions descriptor files should be named `$APP_RESTRICTIONS_FILE`"
            )
        }

        element.childElements().forEach { child ->
            if (child.tagName != TAG_RESTRICTION) {
                context.report(
                    ISSUE,
                    child,
                    context.getLocation(child),
                    "A <restrictions> element can only contain <restriction> elements"
                )
            }
        }
    }

    private fun checkRestriction(context: XmlContext, element: Element) {
        val parent = element.parentNode
        if (parent !is Element ||
            (parent.tagName != TAG_RESTRICTIONS && parent.tagName != TAG_RESTRICTION)
        ) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "A <restriction> element must be nested inside a <restrictions> or another <restriction> element"
            )
        }

        val key = element.getAttributeNS(ANDROID_NAMESPACE, ATTR_KEY)
        if (key.isBlank()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "A <restriction> element must specify an android:key attribute"
            )
        }

        val title = element.getAttributeNS(ANDROID_NAMESPACE, ATTR_TITLE)
        if (title.isBlank()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "A <restriction> element must specify an android:title attribute"
            )
        }

        val type = element.getAttributeNS(ANDROID_NAMESPACE, ATTR_TYPE)
        if (type.isBlank()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "A <restriction> element must specify an android:restrictionType attribute"
            )
        } else if (type !in VALID_TYPES) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Unknown restriction type `$type`; must be one of ${VALID_TYPES.sorted().joinToString()}"
            )
        }

        val defaultValue = element.getAttributeNS(ANDROID_NAMESPACE, ATTR_DEFAULT_VALUE)

        when (type) {
            TYPE_BOOL -> {
                if (defaultValue.isNotBlank() &&
                    defaultValue.lowercase() !in listOf("true", "false")
                ) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "A bool restriction's android:defaultValue must be `true` or `false`"
                    )
                }
            }
            TYPE_INTEGER -> {
                if (defaultValue.isNotBlank() && defaultValue.toIntOrNull() == null) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "An integer restriction's android:defaultValue must be a valid integer"
                    )
                }
            }
            TYPE_CHOICE, TYPE_MULTI_SELECT -> {
                val entries = element.getAttributeNS(ANDROID_NAMESPACE, ATTR_ENTRIES)
                val entryValues = element.getAttributeNS(ANDROID_NAMESPACE, ATTR_ENTRY_VALUES)
                if (entries.isBlank() || entryValues.isBlank()) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "A $type restriction must specify android:entries and android:entryValues"
                    )
                }
            }
            TYPE_BUNDLE, TYPE_BUNDLE_ARRAY -> {
                if (defaultValue.isNotBlank()) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "A $type restriction cannot have an android:defaultValue"
                    )
                }
                element.childElements().forEach { child ->
                    if (child.tagName != TAG_RESTRICTION) {
                        context.report(
                            ISSUE,
                            child,
                            context.getLocation(child),
                            "A $type restriction can only contain nested <restriction> elements"
                        )
                    }
                }
            }
        }

        if (type != TYPE_BUNDLE && type != TYPE_BUNDLE_ARRAY) {
            element.childElements().forEach { child ->
                context.report(
                    ISSUE,
                    child,
                    context.getLocation(child),
                    "Only bundle and bundle_array restrictions can contain nested restrictions"
                )
            }
        }
    }

    private fun Element.childElements(): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = this.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                result.add(node as Element)
            }
        }
        return result
    }

    companion object {
        private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        private const val APP_RESTRICTIONS_FILE = "app_restrictions.xml"

        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"

        private const val ATTR_KEY = "key"
        private const val ATTR_TITLE = "title"
        private const val ATTR_TYPE = "restrictionType"
        private const val ATTR_DEFAULT_VALUE = "defaultValue"
        private const val ATTR_ENTRIES = "entries"
        private const val ATTR_ENTRY_VALUES = "entryValues"

        private const val TYPE_BOOL = "bool"
        private const val TYPE_STRING = "string"
        private const val TYPE_INTEGER = "integer"
        private const val TYPE_CHOICE = "choice"
        private const val TYPE_MULTI_SELECT = "multi-select"
        private const val TYPE_HIDDEN = "hidden"
        private const val TYPE_BUNDLE = "bundle"
        private const val TYPE_BUNDLE_ARRAY = "bundle_array"

        private val VALID_TYPES = setOf(
            TYPE_BOOL,
            TYPE_STRING,
            TYPE_INTEGER,
            TYPE_CHOICE,
            TYPE_MULTI_SELECT,
            TYPE_HIDDEN,
            TYPE_BUNDLE,
            TYPE_BUNDLE_ARRAY
        )

        @JvmField
        val ISSUE = Issue.create(
            "ValidRestrictions",
            "Invalid Restrictions Descriptor",
            """
                Ensures that an application's restrictions XML file is properly formed.

                Application restrictions are declared in `res/xml/app_restrictions.xml` and \
                describe the configuration that a restrictions provider or managed profile \
                owner can enforce. The expected schema is documented by `RestrictionsManager`.
            """.trimIndent(),
            "https://developer.android.com/reference/android/content/RestrictionsManager.html",
            Category.CORRECTNESS,
            5,
            Severity.FATAL,
            Implementation(
                RestrictionsDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
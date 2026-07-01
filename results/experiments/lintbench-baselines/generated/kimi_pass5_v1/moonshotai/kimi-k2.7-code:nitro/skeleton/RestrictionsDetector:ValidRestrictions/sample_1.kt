package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

private const val TAG_RESTRICTIONS = "restrictions"
private const val TAG_RESTRICTION = "restriction"
private const val TAG_BUNDLE = "bundle"
private const val TAG_BUNDLE_ARRAY = "bundle-array"

private const val ATTR_KEY = "key"
private const val ATTR_TITLE = "title"
private const val ATTR_RESTRICTION_TYPE = "restrictionType"
private const val ATTR_ENTRIES = "entries"
private const val ATTR_ENTRY_VALUES = "entryValues"
private const val ATTR_DEFAULT_VALUE = "defaultValue"

private val VALID_TYPES = setOf(
    1, // TYPE_BOOLEAN
    2, // TYPE_CHOICE
    3, // TYPE_MULTI_CHOICE
    4, // TYPE_HIDDEN
    5, // TYPE_INTEGER
    6, // TYPE_STRING
    7, // TYPE_BUNDLE
    8, // TYPE_BUNDLE_ARRAY
)

private val TYPE_NAMES = mapOf(
    1 to "boolean",
    2 to "choice",
    3 to "multi-choice",
    4 to "hidden",
    5 to "integer",
    6 to "string",
    7 to "bundle",
    8 to "bundle-array",
)

private val CHOICE_TYPES = setOf(2, 3)

class RestrictionsDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            RestrictionsDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = """
                An application restrictions XML file must declare a <restrictions> root element. Each
                <restriction> must specify android:key, android:title and android:restrictionType.
                The restriction type must be a known value, and dependent attributes such as
                android:entries/android:entryValues and the default value must match the declared
                type.
            """.trimIndent(),
            moreInfo = "https://developer.android.com/reference/android/content/RestrictionsManager.html",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.XML

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement
        if (root == null || root.tagName != TAG_RESTRICTIONS) {
            val location = if (root != null) context.getLocation(root) else Location.create(context.file)
            context.report(
                ISSUE,
                location,
                "Restrictions XML files must have a <restrictions> root element",
            )
            return
        }

        root.childElements.forEach { child ->
            if (child.tagName == TAG_RESTRICTION) {
                checkRestriction(context, child)
            } else {
                reportError(
                    context,
                    child,
                    "Unexpected element <${child.tagName}> under <restrictions>; only <restriction> is allowed",
                )
            }
        }
    }

    private fun checkRestriction(context: XmlContext, element: Element) {
        val key = getAndroidAttr(element, ATTR_KEY)
        if (key.isBlank()) {
            reportError(context, element, "A <restriction> must specify an android:key attribute")
            return
        }

        if (getAndroidAttr(element, ATTR_TITLE).isBlank()) {
            reportError(context, element, "A <restriction> must specify an android:title attribute")
        }

        val typeString = getAndroidAttr(element, ATTR_RESTRICTION_TYPE)
        if (typeString.isBlank()) {
            reportError(context, element, "A <restriction> must specify an android:restrictionType attribute")
            return
        }

        val type = typeString.toIntOrNull()
        if (type == null || type !in VALID_TYPES) {
            reportError(
                context,
                element,
                "Invalid restriction type \"$typeString\"; must be one of ${VALID_TYPES.joinToString()}",
            )
            return
        }

        if (type in CHOICE_TYPES) {
            if (getAndroidAttr(element, ATTR_ENTRIES).isBlank()) {
                reportError(context, element, "${typeName(type)} restrictions must specify android:entries")
            }
            if (getAndroidAttr(element, ATTR_ENTRY_VALUES).isBlank()) {
                reportError(context, element, "${typeName(type)} restrictions must specify android:entryValues")
            }
        } else {
            if (getAndroidAttr(element, ATTR_ENTRIES).isNotBlank()) {
                reportError(
                    context,
                    element,
                    "android:entries is only valid for choice and multi-choice restrictions",
                )
            }
            if (getAndroidAttr(element, ATTR_ENTRY_VALUES).isNotBlank()) {
                reportError(
                    context,
                    element,
                    "android:entryValues is only valid for choice and multi-choice restrictions",
                )
            }
        }

        val defaultValue = getAndroidAttr(element, ATTR_DEFAULT_VALUE)
        if (defaultValue.isNotBlank()) {
            when (type) {
                1 -> if (defaultValue != "true" && defaultValue != "false") {
                    reportError(
                        context,
                        element,
                        "Boolean restrictions must use \"true\" or \"false\" as the default value",
                    )
                }
                5 -> if (defaultValue.toIntOrNull() == null) {
                    reportError(context, element, "Integer restrictions must use an integer default value")
                }
                7, 8 -> reportError(
                    context,
                    element,
                    "Bundle and bundle-array restrictions cannot specify a default value",
                )
            }
        }

        when (type) {
            7 -> validateBundleRestriction(context, element)
            8 -> validateBundleArrayRestriction(context, element)
        }
    }

    private fun validateBundleRestriction(context: XmlContext, restriction: Element) {
        val bundles = restriction.childElements.filter { it.tagName == TAG_BUNDLE }
        when {
            bundles.isEmpty() -> {
                reportError(context, restriction, "Bundle restrictions must contain a <bundle> element")
                return
            }
            bundles.size > 1 -> {
                reportError(context, restriction, "Bundle restrictions must contain exactly one <bundle> element")
                return
            }
        }

        val bundle = bundles[0]
        bundle.childElements.forEach { child ->
            if (child.tagName == TAG_RESTRICTION) {
                checkRestriction(context, child)
            } else {
                reportError(context, child, "A <bundle> can only contain <restriction> elements")
            }
        }
    }

    private fun validateBundleArrayRestriction(context: XmlContext, restriction: Element) {
        val arrays = restriction.childElements.filter { it.tagName == TAG_BUNDLE_ARRAY }
        when {
            arrays.isEmpty() -> {
                reportError(context, restriction, "Bundle-array restrictions must contain a <bundle-array> element")
                return
            }
            arrays.size > 1 -> {
                reportError(context, restriction, "Bundle-array restrictions must contain exactly one <bundle-array> element")
                return
            }
        }

        val bundleArray = arrays[0]
        bundleArray.childElements.forEach { child ->
            if (child.tagName == TAG_BUNDLE) {
                validateBundleContent(context, child)
            } else {
                reportError(context, child, "A <bundle-array> can only contain <bundle> elements")
            }
        }
    }

    private fun validateBundleContent(context: XmlContext, bundle: Element) {
        bundle.childElements.forEach { child ->
            if (child.tagName == TAG_RESTRICTION) {
                checkRestriction(context, child)
            } else {
                reportError(context, child, "A <bundle> can only contain <restriction> elements")
            }
        }
    }

    private fun getAndroidAttr(element: Element, localName: String): String =
        element.getAttributeNS(ANDROID_URI, localName) ?: ""

    private fun reportError(context: XmlContext, node: Node, message: String) {
        context.report(ISSUE, context.getLocation(node), message)
    }

    private fun typeName(type: Int): String = TYPE_NAMES[type] ?: "unknown"

    private val Element.childElements: List<Element>
        get() {
            val elements = mutableListOf<Element>()
            var child = firstChild
            while (child != null) {
                if (child.nodeType == Node.ELEMENT_NODE) {
                    elements.add(child as Element)
                }
                child = child.nextSibling
            }
            return elements
        }
}
package com.android.tools.lint.checks

import com.android.SdkConstants
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

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        const val WEARABLE_CONFIGURATION_ACTION = "wearableConfigurationAction"
        const val WATCH_FACE_EDITOR = "WATCH_FACE_EDITOR"
        const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                When a watch face service declares a `wearableConfigurationAction` metadata value of `WATCH_FACE_EDITOR`, there must be exactly one activity in the same package with a matching intent filter. Declaring more than one such activity is an error.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    private var manifestMinSdk: Int = -1
    private val serviceMetaDataElements = mutableListOf<Element>()
    private val matchingActivities = mutableListOf<Element>()

    override fun getApplicableElements(): Collection<String> =
        listOf(SdkConstants.TAG_SERVICE, SdkConstants.TAG_ACTIVITY)

    override fun beforeCheckFile(context: Context) {
        manifestMinSdk = -1
        serviceMetaDataElements.clear()
        matchingActivities.clear()
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        manifestMinSdk = document.documentElement
            ?.children(SdkConstants.TAG_USES_SDK)
            ?.firstOrNull()
            ?.getAttr(SdkConstants.ATTR_MIN_SDK_VERSION)
            ?.toIntOrNull()
            ?: context.mainProject?.minSdk
            ?: context.project?.minSdk
            ?: -1
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.localName ?: element.tagName) {
            SdkConstants.TAG_SERVICE -> {
                val metaData = element.children(SdkConstants.TAG_META_DATA).firstOrNull {
                    it.getAttr(SdkConstants.ATTR_NAME) == WEARABLE_CONFIGURATION_ACTION &&
                        it.getAttr(SdkConstants.ATTR_VALUE) == WATCH_FACE_EDITOR
                }
                if (metaData != null) {
                    serviceMetaDataElements.add(metaData)
                }
            }
            SdkConstants.TAG_ACTIVITY -> {
                val requiresCategory = manifestMinSdk in 0..29
                for (intentFilter in element.children(SdkConstants.TAG_INTENT_FILTER)) {
                    val hasAction = intentFilter.children(SdkConstants.TAG_ACTION).any {
                        it.getAttr(SdkConstants.ATTR_NAME) == WATCH_FACE_EDITOR
                    }
                    if (!hasAction) continue

                    if (requiresCategory) {
                        val hasCategory = intentFilter.children(SdkConstants.TAG_CATEGORY).any {
                            it.getAttr(SdkConstants.ATTR_NAME) == WEARABLE_CONFIGURATION_CATEGORY
                        }
                        if (!hasCategory) continue
                    }

                    matchingActivities.add(element)
                    break
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        if (serviceMetaDataElements.isEmpty()) return
        if (matchingActivities.size <= 1) return

        val xmlContext = context as XmlContext
        val manifestPackage = xmlContext.document.documentElement
            ?.getAttribute(SdkConstants.ATTR_PACKAGE)
            ?: ""

        for (i in 1 until matchingActivities.size) {
            val activity = matchingActivities[i]
            xmlContext.report(
                ISSUE,
                xmlContext.getLocation(activity),
                "Duplicate watch face configuration activities found for action `$WATCH_FACE_EDITOR` in package `$manifestPackage`"
            )
        }
    }

    private fun Element.children(tagName: String): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = childNodes ?: return result
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element && (node.localName ?: node.tagName) == tagName) {
                result.add(node)
            }
        }
        return result
    }

    private fun Element.getAttr(name: String): String? {
        val namespaced = getAttributeNS(SdkConstants.ANDROID_URI, name)
        if (namespaced.isNotEmpty()) return namespaced
        val unprefixed = getAttribute(name)
        return if (unprefixed.isNotEmpty()) unprefixed else null
    }
}
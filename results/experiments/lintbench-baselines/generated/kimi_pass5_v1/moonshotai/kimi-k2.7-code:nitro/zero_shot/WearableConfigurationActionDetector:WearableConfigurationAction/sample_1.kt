package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class WearableConfigurationActionDetector : ResourceXmlDetector() {

    private val services = mutableListOf<ServiceMetadata>()
    private val activities = mutableListOf<ActivityMetadata>()

    override fun getApplicableElements(): Collection<String>? = listOf(
        SdkConstants.TAG_SERVICE,
        SdkConstants.TAG_ACTIVITY
    )

    override fun visitElement(context: XmlContext, element: Element) {
        val packageName = context.document.documentElement?.getAttribute(SdkConstants.ATTR_PACKAGE) ?: return

        when (element.tagName) {
            SdkConstants.TAG_SERVICE -> {
                val metadata = element.childElements(SdkConstants.TAG_METADATA)
                    .firstOrNull {
                        it.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME) ==
                                WEARABLE_CONFIGURATION_ACTION_METADATA
                    } ?: return
                val value = metadata.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE)
                if (value == WATCH_FACE_EDITOR_VALUE) {
                    services.add(ServiceMetadata(metadata, packageName))
                }
            }
            SdkConstants.TAG_ACTIVITY -> {
                val hasEditor = element.childElements(SdkConstants.TAG_INTENT_FILTER).any { filter ->
                    filter.childElements(SdkConstants.TAG_ACTION).any { action ->
                        action.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME) ==
                                WATCH_FACE_EDITOR_ACTION
                    }
                }
                if (hasEditor) {
                    val hasCategory = element.childElements(SdkConstants.TAG_INTENT_FILTER).any { filter ->
                        filter.childElements(SdkConstants.TAG_CATEGORY).any { category ->
                            category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME) ==
                                    WEARABLE_CONFIGURATION_CATEGORY
                        }
                    }
                    activities.add(ActivityMetadata(element, packageName, hasCategory))
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        if (services.isEmpty()) return

        val xmlContext = context as XmlContext
        val minSdk = xmlContext.project.minSdkVersion.featureLevel

        for (service in services) {
            val matching = activities.filter { it.packageName == service.packageName }

            if (matching.isEmpty()) {
                xmlContext.report(
                    ISSUE,
                    service.metadata,
                    xmlContext.getLocation(service.metadata),
                    "Watch face service declares `${WEARABLE_CONFIGURATION_ACTION_METADATA}` with " +
                            "value `${WATCH_FACE_EDITOR_VALUE}` but no matching activity defines " +
                            "an intent filter with action `${WATCH_FACE_EDITOR_ACTION}`."
                )
            } else if (minSdk < 30 && matching.none { it.hasCategory }) {
                xmlContext.report(
                    ISSUE,
                    service.metadata,
                    xmlContext.getLocation(service.metadata),
                            "When `minSdkVersion` is less than 30, the configuration activity for " +
                            "`${WATCH_FACE_EDITOR_VALUE}` must include category " +
                            "`${WEARABLE_CONFIGURATION_CATEGORY}` in its intent filter."
                )
            }
        }

        services.clear()
        activities.clear()
    }

    private data class ServiceMetadata(val metadata: Element, val packageName: String)
    private data class ActivityMetadata(
        val activity: Element,
        val packageName: String,
        val hasCategory: Boolean
    )

    private fun Element.childElements(name: String): List<Element> =
        (0 until childNodes.length)
            .mapNotNull { childNodes.item(it) }
            .filter { it.nodeType == Node.ELEMENT_NODE && it.nodeName == name }
            .map { it as Element }

    companion object {
        private const val WEARABLE_CONFIGURATION_ACTION_METADATA =
            "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val WATCH_FACE_EDITOR_VALUE = "WATCH_FACE_EDITOR"
        private const val WATCH_FACE_EDITOR_ACTION =
            "com.google.android.wearable.watchface.WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Watch face configuration action metadata must match an activity",
            explanation = """
                When a watch face service declares the metadata
                `${WEARABLE_CONFIGURATION_ACTION_METADATA}` with value `${WATCH_FACE_EDITOR_VALUE}`,
                there must be an activity in the same package with an intent filter whose action
                is `${WATCH_FACE_EDITOR_ACTION}`. If `minSdkVersion` is less than 30, that intent
                filter must also include the category
                `${WEARABLE_CONFIGURATION_CATEGORY}`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}
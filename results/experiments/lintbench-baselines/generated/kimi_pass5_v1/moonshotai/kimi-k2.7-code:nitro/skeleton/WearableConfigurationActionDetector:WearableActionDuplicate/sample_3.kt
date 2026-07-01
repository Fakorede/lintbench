package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Detector.XmlScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
private const val META_DATA_WEARABLE_CONFIGURATION_ACTION = "wearableConfigurationAction"
private const val ACTION_WEARABLE_CONFIGURATION = "WATCH_FACE_EDITOR"
private const val CATEGORY_WEARABLE_CONFIGURATION =
    "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.MANIFEST_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                When a watch face service declares the wearableConfigurationAction metadata with value WATCH_FACE_EDITOR, there must be only one corresponding configuration activity in the manifest with the required intent filter.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun checkMergedProject(context: Context) {
        val document = context.mainProject.mergedManifest ?: return
        val manifest = document.documentElement ?: return
        val application =
            manifest.getElementsByTagName("application").item(0) as? org.w3c.dom.Element ?: return

        val requireCategory = context.mainProject.minSdk < 30

        var needsConfiguration = false
        val services = application.getElementsByTagName("service")
        for (i in 0 until services.length) {
            val service = services.item(i) as? org.w3c.dom.Element ?: continue
            if (service.hasMetadataValue(
                    META_DATA_WEARABLE_CONFIGURATION_ACTION,
                    ACTION_WEARABLE_CONFIGURATION
                )
            ) {
                needsConfiguration = true
                break
            }
        }
        if (!needsConfiguration) {
            return
        }

        val activities = application.getElementsByTagName("activity")
        val matchingActivities = mutableListOf<org.w3c.dom.Element>()
        for (i in 0 until activities.length) {
            val activity = activities.item(i) as? org.w3c.dom.Element ?: continue
            if (activity.isWearableConfigurationActivity(requireCategory)) {
                matchingActivities.add(activity)
            }
        }

        if (matchingActivities.size > 1) {
            for (i in 1 until matchingActivities.size) {
                context.report(
                    ISSUE,
                    context.getLocation(matchingActivities[i]),
                    "Duplicate watch face configuration activities found",
                )
            }
        }
    }
}

private fun org.w3c.dom.Element.hasMetadataValue(name: String, value: String): Boolean {
    val metadata = getElementsByTagName("meta-data")
    for (i in 0 until metadata.length) {
        val node = metadata.item(i) as? org.w3c.dom.Element ?: continue
        if (node.getAttributeNS(ANDROID_NS, "name") == name &&
            node.getAttributeNS(ANDROID_NS, "value") == value
        ) {
            return true
        }
    }
    return false
}

private fun org.w3c.dom.Element.isWearableConfigurationActivity(requireCategory: Boolean): Boolean {
    val filters = getElementsByTagName("intent-filter")
    for (i in 0 until filters.length) {
        val filter = filters.item(i) as? org.w3c.dom.Element ?: continue
        if (filter.hasChildWithAttribute(
                "action",
                ANDROID_NS,
                "name",
                ACTION_WEARABLE_CONFIGURATION
            ) &&
            (!requireCategory || filter.hasChildWithAttribute(
                "category",
                ANDROID_NS,
                "name",
                CATEGORY_WEARABLE_CONFIGURATION
            ))
        ) {
            return true
        }
    }
    return false
}

private fun org.w3c.dom.Element.hasChildWithAttribute(
    tag: String,
    namespace: String,
    attribute: String,
    value: String,
): Boolean {
    val children = getElementsByTagName(tag)
    for (i in 0 until children.length) {
        val child = children.item(i) as? org.w3c.dom.Element ?: continue
        if (child.getAttributeNS(namespace, attribute) == value) {
            return true
        }
    }
    return false
}
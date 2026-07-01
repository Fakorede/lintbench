package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? = listOf("manifest")

    override fun visitElement(context: XmlContext, element: Element) {
        val activities = element.getElementsByTagName(TAG_ACTIVITY)
        val services = element.getElementsByTagName(TAG_SERVICE)

        val configActivities = mutableListOf<Element>()
        val servicesRequiringConfig = mutableListOf<Element>()

        for (i in 0 until services.length) {
            val service = services.item(i) as Element
            if (hasWearableConfigMetadata(service)) {
                servicesRequiringConfig.add(service)
            }
        }

        for (i in 0 until activities.length) {
            val activity = activities.item(i) as Element
            if (hasConfigIntentFilter(context, activity)) {
                configActivities.add(activity)
            }
        }

        if (configActivities.size > 1) {
            for (activity in configActivities) {
                context.report(
                    ISSUE,
                    activity,
                    context.getLocation(activity),
                    "Duplicate watch face configuration activities found"
                )
            }
        } else if (configActivities.isEmpty() && servicesRequiringConfig.isNotEmpty()) {
            for (service in servicesRequiringConfig) {
                context.report(
                    ISSUE,
                    service,
                    context.getLocation(service),
                    "Missing watch face configuration activity"
                )
            }
        }
    }

    private fun hasWearableConfigMetadata(service: Element): Boolean {
        val children = service.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == TAG_META_DATA) {
                val name = child.getAttribute("android:name")
                val value = child.getAttribute("android:value")
                if (name.endsWith("wearableConfigurationAction") && value == "WATCH_FACE_EDITOR") {
                    return true
                }
            }
        }
        return false
    }

    private fun hasConfigIntentFilter(context: XmlContext, activity: Element): Boolean {
        val minSdk = context.project.minSdkVersion?.apiLevel ?: 1
        val intentFilters = activity.getElementsByTagName(TAG_INTENT_FILTER)
        for (i in 0 until intentFilters.length) {
            val filter = intentFilters.item(i) as Element
            var hasAction = false
            var hasCategory = false
            val children = filter.childNodes
            for (j in 0 until children.length) {
                val child = children.item(j)
                if (child is Element) {
                    val name = child.getAttribute("android:name")
                    when (child.tagName) {
                        TAG_ACTION -> if (name.endsWith("WATCH_FACE_EDITOR")) hasAction = true
                        TAG_CATEGORY -> if (name == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") hasCategory = true
                    }
                }
            }
            if (hasAction && (minSdk >= 30 || hasCategory)) return true
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = "If a watch face service defines wearableConfigurationAction metadata with the value WATCH_FACE_EDITOR, there should be exactly one activity in the same package with an intent filter for WATCH_FACE_EDITOR (and the WEARABLE_CONFIGURATION category if minSdkVersion < 30).",
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
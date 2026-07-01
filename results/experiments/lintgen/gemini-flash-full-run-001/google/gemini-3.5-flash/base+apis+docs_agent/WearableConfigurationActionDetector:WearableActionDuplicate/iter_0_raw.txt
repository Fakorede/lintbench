package com.android.tools.lint.checks

import com.android.SdkConstants
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

    override fun getApplicableElements(): Collection<String> {
        return listOf("manifest")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val applicationList = element.getElementsByTagName("application")
        if (applicationList.length == 0) return
        val application = applicationList.item(0) as Element

        val services = mutableListOf<Element>()
        val activities = mutableListOf<Element>()

        val childNodes = application.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                if (child.tagName == "service") {
                    services.add(child)
                } else if (child.tagName == "activity") {
                    activities.add(child)
                }
            }
        }

        val servicesWithMeta = mutableListOf<Element>()
        for (service in services) {
            val metaDatas = service.getElementsByTagName("meta-data")
            for (j in 0 until metaDatas.length) {
                val metaData = metaDatas.item(j) as Element
                val name = metaData.getAttributeNS(SdkConstants.ANDROID_URI, "name")
                if (name == "com.google.android.wearable.watchface.wearableConfigurationAction") {
                    val value = metaData.getAttributeNS(SdkConstants.ANDROID_URI, "value")
                    if (value != null && value.contains("WATCH_FACE_EDITOR")) {
                        servicesWithMeta.add(service)
                    }
                }
            }
        }

        val matchingActivities = mutableListOf<Element>()
        val activitiesMissingCategory = mutableListOf<Element>()
        val minSdkVersion = context.project.minSdkVersion.apiLevel

        for (activity in activities) {
            val intentFilters = activity.getElementsByTagName("intent-filter")
            var hasAction = false
            var hasCategory = false
            for (j in 0 until intentFilters.length) {
                val filter = intentFilters.item(j) as Element
                val actions = filter.getElementsByTagName("action")
                for (k in 0 until actions.length) {
                    val action = actions.item(k) as Element
                    val actionName = action.getAttributeNS(SdkConstants.ANDROID_URI, "name")
                    if (actionName != null && actionName.contains("WATCH_FACE_EDITOR")) {
                        hasAction = true
                    }
                }
                val categories = filter.getElementsByTagName("category")
                for (k in 0 until categories.length) {
                    val category = categories.item(k) as Element
                    val catName = category.getAttributeNS(SdkConstants.ANDROID_URI, "name")
                    if (catName == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
                        hasCategory = true
                    }
                }
            }

            if (hasAction) {
                matchingActivities.add(activity)
                if (minSdkVersion < 30 && !hasCategory) {
                    activitiesMissingCategory.add(activity)
                }
            }
        }

        val hasService = servicesWithMeta.isNotEmpty()
        val hasActivity = matchingActivities.isNotEmpty()

        if (hasService && !hasActivity) {
            for (service in servicesWithMeta) {
                context.report(
                    ISSUE,
                    service,
                    context.getNameLocation(service),
                    "Watch face service defines `wearableConfigurationAction` but no matching configuration activity was found"
                )
            }
        } else if (!hasService && hasActivity) {
            for (activity in matchingActivities) {
                context.report(
                    ISSUE,
                    activity,
                    context.getNameLocation(activity),
                    "Configuration activity found but no watch face service defines the corresponding `wearableConfigurationAction` metadata"
                )
            }
        } else if (hasService && hasActivity) {
            if (matchingActivities.size > 1) {
                for (activity in matchingActivities) {
                    context.report(
                        ISSUE,
                        activity,
                        context.getNameLocation(activity),
                        "Duplicate watch face configuration activities found"
                    )
                }
            }

            for (activity in activitiesMissingCategory) {
                context.report(
                    ISSUE,
                    activity,
                    context.getNameLocation(activity),
                    "Configuration activity is missing the `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category (required for minSdkVersion < 30)"
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                If and only if a watch face service defines `wearableConfigurationAction` metadata, \
                with the value `WATCH_FACE_EDITOR`, there should be an activity in the same package, \
                which has an intent filter for `WATCH_FACE_EDITOR` (with \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` if minSdkVersion is less than 30).
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
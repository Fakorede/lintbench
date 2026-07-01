package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    private val services = mutableListOf<ServiceInfo>()
    private val activities = mutableListOf<ActivityInfo>()

    override fun beforeCheckRootProject(context: Context) {
        services.clear()
        activities.clear()
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("service", "activity")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName
        if (tagName == "service") {
            var hasConfig = false
            val metaDataNodes = element.getElementsByTagName("meta-data")
            for (j in 0 until metaDataNodes.length) {
                val metaData = metaDataNodes.item(j) as Element
                val name = metaData.getAttributeNS(ANDROID_URI, "name")
                val value = metaData.getAttributeNS(ANDROID_URI, "value")
                if (name == "com.google.android.wearable.watchface.wearableConfigurationAction" &&
                    value == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR") {
                    hasConfig = true
                    break
                }
            }
            services.add(ServiceInfo(element, context.getLocation(element), hasConfig))
        } else if (tagName == "activity") {
            var hasAction = false
            var hasCategory = false
            val intentFilters = element.getElementsByTagName("intent-filter")
            for (j in 0 until intentFilters.length) {
                val intentFilter = intentFilters.item(j) as Element
                val actions = intentFilter.getElementsByTagName("action")
                var currentFilterHasAction = false
                for (k in 0 until actions.length) {
                    val action = actions.item(k) as Element
                    val actionName = action.getAttributeNS(ANDROID_URI, "name")
                    if (actionName == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR") {
                        hasAction = true
                        currentFilterHasAction = true
                        break
                    }
                }
                if (currentFilterHasAction) {
                    val categories = intentFilter.getElementsByTagName("category")
                    for (k in 0 until categories.length) {
                        val category = categories.item(k) as Element
                        val categoryName = category.getAttributeNS(ANDROID_URI, "name")
                        if (categoryName == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
                            hasCategory = true
                            break
                        }
                    }
                }
            }
            activities.add(ActivityInfo(element, context.getLocation(element), hasAction, hasCategory))
        }
    }

    override fun checkMergedProject(context: Context) {
        val minSdkVersion = context.project.minSdkVersion.apiLevel
        val configServices = services.filter { it.hasConfig }
        val configActivities = activities.filter { activity ->
            activity.hasAction && (minSdkVersion >= 30 || activity.hasCategory)
        }

        if (configActivities.size > 1) {
            for (activity in configActivities) {
                context.report(
                    ISSUE,
                    activity.location,
                    "Duplicate watch face configuration activities found"
                )
            }
        }

        if (configServices.isNotEmpty() && configActivities.isEmpty()) {
            for (service in configServices) {
                context.report(
                    ISSUE,
                    service.location,
                    "A watch face service defines `wearableConfigurationAction` but no matching configuration activity was found"
                )
            }
        } else if (configServices.isEmpty() && configActivities.isNotEmpty()) {
            for (activity in configActivities) {
                context.report(
                    ISSUE,
                    activity.location,
                    "A watch face configuration activity was found, but no watch face service defines the `wearableConfigurationAction` metadata"
                )
            }
        }
    }

    private class ServiceInfo(val element: Element, val location: Location, val hasConfig: Boolean)
    private class ActivityInfo(val element: Element, val location: Location, val hasAction: Boolean, val hasCategory: Boolean)

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = "If and only if a watch face service defines `wearableConfigurationAction` metadata, with the value `WATCH_FACE_EDITOR`, there should be an activity in the same package, which has an intent filter for `WATCH_FACE_EDITOR` (with com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION if minSdkVersion is less than 30).",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(WearableConfigurationActionDetector::class.java, Scope.MANIFEST_SCOPE)
        )
    }
}
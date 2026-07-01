package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), Detector.XmlScanner {

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return

        val services = mutableListOf<Element>()
        val activities = mutableListOf<Element>()

        val serviceNodes = root.getElementsByTagName("service")
        for (i in 0 until serviceNodes.length) {
            val service = serviceNodes.item(i) as Element
            if (hasWearableConfigMetadata(service)) {
                services.add(service)
            }
        }

        val activityNodes = root.getElementsByTagName("activity")
        for (i in 0 until activityNodes.length) {
            val activity = activityNodes.item(i) as Element
            if (hasWatchFaceEditorAction(activity)) {
                activities.add(activity)
            }
        }

        val minSdk = context.project.minSdkVersion.apiLevel

        // Check 1: Duplicate activities
        if (activities.size > 1) {
            for (activity in activities) {
                context.report(
                    ISSUE,
                    activity,
                    context.getNameLocation(activity),
                    "Duplicate watch face configuration activities found"
                )
            }
            return
        }

        // Check 2: If service exists, activity must exist
        if (services.isNotEmpty() && activities.isEmpty()) {
            context.report(
                ISSUE,
                services.first(),
                context.getNameLocation(services.first()),
                "Watch face service defines wearableConfigurationAction but no matching activity with WATCH_FACE_EDITOR intent filter was found"
            )
        }

        // Check 3: If activity exists, service must exist
        if (activities.isNotEmpty() && services.isEmpty()) {
            context.report(
                ISSUE,
                activities.first(),
                context.getNameLocation(activities.first()),
                "Activity with WATCH_FACE_EDITOR intent filter found but no watch face service defines wearableConfigurationAction"
            )
        }

        // Check 4: If minSdk < 30, activity must have the category
        if (services.isNotEmpty() && activities.isNotEmpty() && minSdk < 30) {
            val activity = activities.first()
            if (!hasWearableConfigCategory(activity)) {
                context.report(
                    ISSUE,
                    activity,
                    context.getNameLocation(activity),
                    "Watch face configuration activity is missing the WEARABLE_CONFIGURATION category required for minSdkVersion < 30"
                )
            }
        }
    }

    private fun hasWearableConfigMetadata(service: Element): Boolean {
        val metaDataNodes = service.getElementsByTagName("meta-data")
        for (i in 0 until metaDataNodes.length) {
            val metaData = metaDataNodes.item(i) as Element
            val name = metaData.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            val value = metaData.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE)
            if ((name == "com.google.android.wearable.watchface.wearableConfigurationAction" || name == "wearableConfigurationAction") &&
                (value == "com.google.android.wearable.watchface.wearableConfigurationAction.WATCH_FACE_EDITOR" || value == "WATCH_FACE_EDITOR")) {
                return true
            }
        }
        return false
    }

    private fun hasWatchFaceEditorAction(activity: Element): Boolean {
        val intentFilters = activity.getElementsByTagName("intent-filter")
        for (i in 0 until intentFilters.length) {
            val filter = intentFilters.item(i) as Element
            val actions = filter.getElementsByTagName("action")
            for (j in 0 until actions.length) {
                val action = actions.item(j) as Element
                val name = action.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                if (name == "com.google.android.wearable.watchface.wearableConfigurationAction.WATCH_FACE_EDITOR" || name == "WATCH_FACE_EDITOR") {
                    return true
                }
            }
        }
        return false
    }

    private fun hasWearableConfigCategory(activity: Element): Boolean {
        val intentFilters = activity.getElementsByTagName("intent-filter")
        for (i in 0 until intentFilters.length) {
            val filter = intentFilters.item(i) as Element
            val categories = filter.getElementsByTagName("category")
            for (j in 0 until categories.length) {
                val category = categories.item(j) as Element
                val name = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                if (name == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION" || name == "WEARABLE_CONFIGURATION") {
                    return true
                }
            }
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                If and only if a watch face service defines `wearableConfigurationAction` metadata,
                with the value `WATCH_FACE_EDITOR`, there should be an activity in the same package,
                which has an intent filter for `WATCH_FACE_EDITOR` (with
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
package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_MIN_SDK_VERSION
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.SdkConstants.TAG_USES_SDK
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.XmlUtils
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val WATCH_FACE_EDITOR_ACTION =
            "androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val WEARABLE_CONFIGURATION_ACTION_METADATA =
            "com.google.android.wearable.watchface.wearableConfigurationAction"

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation =
                """
                If a watch face service defines `wearableConfigurationAction` metadata with the \
                value `WATCH_FACE_EDITOR`, there should be exactly one activity in the same \
                package which has an intent filter for `WATCH_FACE_EDITOR` \
                (with `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` \
                if minSdkVersion is less than 30).

                See https://developer.android.com/training/wearables/watch-faces/configuration
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    override fun checkMergedProject(context: Context) {
        val document = context.mainProject.mergedManifest?.documentElement ?: return

        // Get minSdkVersion
        val minSdkVersion = getMinSdkVersion(document)

        // Find the application element
        val application = XmlUtils.getFirstSubTagByName(document, TAG_APPLICATION) ?: return

        // Collect all services that have wearableConfigurationAction = WATCH_FACE_EDITOR
        val watchFaceServices = mutableListOf<Element>()
        var service = XmlUtils.getFirstSubTagByName(application, TAG_SERVICE)
        while (service != null) {
            if (serviceHasWatchFaceEditorMetadata(service)) {
                watchFaceServices.add(service)
            }
            service = XmlUtils.getNextTagByName(service, TAG_SERVICE)
        }

        if (watchFaceServices.isEmpty()) return

        // Collect all activities that have WATCH_FACE_EDITOR intent filter
        val configActivities = mutableListOf<Element>()
        var activity = XmlUtils.getFirstSubTagByName(application, TAG_ACTIVITY)
        while (activity != null) {
            if (activityHasWatchFaceEditorAction(activity, minSdkVersion)) {
                configActivities.add(activity)
            }
            activity = XmlUtils.getNextTagByName(activity, TAG_ACTIVITY)
        }

        // For each watch face service, check configuration activity count
        for (watchFaceService in watchFaceServices) {
            if (configActivities.size > 1) {
                // Report on each duplicate activity beyond the first
                for (i in 1 until configActivities.size) {
                    val duplicateActivity = configActivities[i]
                    val location = getElementLocation(context, duplicateActivity)
                    context.report(
                        ISSUE,
                        location,
                        "Duplicate watch face configuration activities found: there should " +
                            "be exactly one activity with an intent filter for " +
                            "`$WATCH_FACE_EDITOR_ACTION`"
                    )
                }
            } else if (configActivities.isEmpty()) {
                val location = getElementLocation(context, watchFaceService)
                context.report(
                    ISSUE,
                    location,
                    "Watch face service defines `wearableConfigurationAction` metadata but " +
                        "no activity with an intent filter for `$WATCH_FACE_EDITOR_ACTION` " +
                        "was found"
                )
            }
        }
    }

    private fun serviceHasWatchFaceEditorMetadata(service: Element): Boolean {
        var metaData = XmlUtils.getFirstSubTagByName(service, TAG_META_DATA)
        while (metaData != null) {
            val name = metaData.getAttributeNS(ANDROID_URI, ATTR_NAME)
            val value = metaData.getAttributeNS(ANDROID_URI, ATTR_VALUE)
            if (name == WEARABLE_CONFIGURATION_ACTION_METADATA &&
                value == WATCH_FACE_EDITOR_ACTION
            ) {
                return true
            }
            metaData = XmlUtils.getNextTagByName(metaData, TAG_META_DATA)
        }
        return false
    }

    private fun activityHasWatchFaceEditorAction(activity: Element, minSdkVersion: Int): Boolean {
        var intentFilter = XmlUtils.getFirstSubTagByName(activity, TAG_INTENT_FILTER)
        while (intentFilter != null) {
            var hasWatchFaceEditorAction = false
            var hasWearableConfigurationCategory = false

            var action = XmlUtils.getFirstSubTagByName(intentFilter, TAG_ACTION)
            while (action != null) {
                val actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (actionName == WATCH_FACE_EDITOR_ACTION) {
                    hasWatchFaceEditorAction = true
                }
                action = XmlUtils.getNextTagByName(action, TAG_ACTION)
            }

            var category = XmlUtils.getFirstSubTagByName(intentFilter, TAG_CATEGORY)
            while (category != null) {
                val categoryName = category.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (categoryName == WEARABLE_CONFIGURATION_CATEGORY) {
                    hasWearableConfigurationCategory = true
                }
                category = XmlUtils.getNextTagByName(category, TAG_CATEGORY)
            }

            if (hasWatchFaceEditorAction) {
                if (minSdkVersion >= 30) {
                    return true
                } else if (hasWearableConfigurationCategory) {
                    return true
                }
            }

            intentFilter = XmlUtils.getNextTagByName(intentFilter, TAG_INTENT_FILTER)
        }
        return false
    }

    private fun getMinSdkVersion(document: Element): Int {
        val usesSdk = XmlUtils.getFirstSubTagByName(document, TAG_USES_SDK)
        if (usesSdk != null) {
            val minSdk = usesSdk.getAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION)
            if (minSdk.isNotEmpty()) {
                return minSdk.toIntOrNull() ?: 1
            }
        }
        return 1
    }

    private fun getElementLocation(context: Context, element: Element): com.android.tools.lint.detector.api.Location {
        // Try to get a proper location from the element
        val nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)
        return if (nameAttr != null) {
            com.android.tools.lint.detector.api.Location.create(context.mainProject.manifestFiles.firstOrNull() ?: context.file)
        } else {
            com.android.tools.lint.detector.api.Location.create(context.mainProject.manifestFiles.firstOrNull() ?: context.file)
        }
    }
}
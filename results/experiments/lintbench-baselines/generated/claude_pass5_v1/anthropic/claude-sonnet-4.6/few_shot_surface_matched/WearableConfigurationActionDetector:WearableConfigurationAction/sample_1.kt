package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Project
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
        private const val META_DATA_WEARABLE_CONFIGURATION_ACTION =
            "com.google.android.wearable.watchface.wearableConfigurationAction"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wearable configuration action metadata must match an activity",
            explanation = """
                When a watch face service defines `wearableConfigurationAction` metadata with the \
                value `WATCH_FACE_EDITOR`, there should be an activity in the same package that \
                has an intent filter for `WATCH_FACE_EDITOR`. If `minSdkVersion` is less than 30, \
                the intent filter should also include the \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category.

                See https://developer.android.com/training/wearables/watch-faces/configuration for \
                more details.
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
        val mainProject: Project = context.mainProject
        val manifestDocument = mainProject.mergedManifest?.documentElement ?: return

        val minSdk = mainProject.minSdkVersion.featureLevel

        // Find the application element
        val applicationElement = XmlUtils.getFirstSubTagByName(manifestDocument, TAG_APPLICATION)
            ?: return

        // Collect all services that have wearableConfigurationAction metadata = WATCH_FACE_EDITOR
        val servicesWithConfig = mutableListOf<Element>()
        var serviceTag = XmlUtils.getFirstSubTagByName(applicationElement, TAG_SERVICE)
        while (serviceTag != null) {
            if (serviceHasWatchFaceEditorConfig(serviceTag)) {
                servicesWithConfig.add(serviceTag)
            }
            serviceTag = XmlUtils.getNextTagByName(serviceTag, TAG_SERVICE)
        }

        if (servicesWithConfig.isEmpty()) {
            return
        }

        // Collect all activities that have an intent filter with WATCH_FACE_EDITOR action
        val activitiesWithEditor = mutableListOf<Element>()
        var activityTag = XmlUtils.getFirstSubTagByName(applicationElement, TAG_ACTIVITY)
        while (activityTag != null) {
            if (activityHasWatchFaceEditorIntentFilter(activityTag, minSdk)) {
                activitiesWithEditor.add(activityTag)
            }
            activityTag = XmlUtils.getNextTagByName(activityTag, TAG_ACTIVITY)
        }

        // For each service with the config, check that a matching activity exists
        for (service in servicesWithConfig) {
            val hasMatchingActivity = activitiesWithEditor.isNotEmpty()
            if (!hasMatchingActivity) {
                // Try to find the meta-data element to report the location on
                val metaDataElement = findWatchFaceEditorMetaData(service)
                val location = if (metaDataElement != null) {
                    context.getLocation(metaDataElement)
                } else {
                    context.getLocation(service)
                }

                val message = if (minSdk < 30) {
                    "This watch face service has `wearableConfigurationAction` metadata, but " +
                        "no matching activity was found in the same package with an intent " +
                        "filter for `$WATCH_FACE_EDITOR_ACTION` and category " +
                        "`$WEARABLE_CONFIGURATION_CATEGORY` (required when minSdkVersion < 30)"
                } else {
                    "This watch face service has `wearableConfigurationAction` metadata, but " +
                        "no matching activity was found in the same package with an intent " +
                        "filter for `$WATCH_FACE_EDITOR_ACTION`"
                }

                context.report(ISSUE, location, message)
            }
        }
    }

    private fun serviceHasWatchFaceEditorConfig(serviceElement: Element): Boolean {
        var metaData = XmlUtils.getFirstSubTagByName(serviceElement, TAG_META_DATA)
        while (metaData != null) {
            val name = metaData.getAttributeNS(ANDROID_URI, ATTR_NAME)
            val value = metaData.getAttributeNS(ANDROID_URI, "value")
            if (name == META_DATA_WEARABLE_CONFIGURATION_ACTION &&
                value == WATCH_FACE_EDITOR_ACTION
            ) {
                return true
            }
            metaData = XmlUtils.getNextTagByName(metaData, TAG_META_DATA)
        }
        return false
    }

    private fun findWatchFaceEditorMetaData(serviceElement: Element): Element? {
        var metaData = XmlUtils.getFirstSubTagByName(serviceElement, TAG_META_DATA)
        while (metaData != null) {
            val name = metaData.getAttributeNS(ANDROID_URI, ATTR_NAME)
            val value = metaData.getAttributeNS(ANDROID_URI, "value")
            if (name == META_DATA_WEARABLE_CONFIGURATION_ACTION &&
                value == WATCH_FACE_EDITOR_ACTION
            ) {
                return metaData
            }
            metaData = XmlUtils.getNextTagByName(metaData, TAG_META_DATA)
        }
        return null
    }

    private fun activityHasWatchFaceEditorIntentFilter(
        activityElement: Element,
        minSdk: Int
    ): Boolean {
        var intentFilter = XmlUtils.getFirstSubTagByName(activityElement, TAG_INTENT_FILTER)
        while (intentFilter != null) {
            if (intentFilterMatchesWatchFaceEditor(intentFilter, minSdk)) {
                return true
            }
            intentFilter = XmlUtils.getNextTagByName(intentFilter, TAG_INTENT_FILTER)
        }
        return false
    }

    private fun intentFilterMatchesWatchFaceEditor(
        intentFilter: Element,
        minSdk: Int
    ): Boolean {
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

        if (!hasWatchFaceEditorAction) {
            return false
        }

        if (minSdk >= 30) {
            // No category requirement for minSdk >= 30
            return true
        }

        // For minSdk < 30, also need the WEARABLE_CONFIGURATION category
        var category = XmlUtils.getFirstSubTagByName(intentFilter, TAG_CATEGORY)
        while (category != null) {
            val categoryName = category.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (categoryName == WEARABLE_CONFIGURATION_CATEGORY) {
                hasWearableConfigurationCategory = true
            }
            category = XmlUtils.getNextTagByName(category, TAG_CATEGORY)
        }

        return hasWearableConfigurationCategory
    }
}
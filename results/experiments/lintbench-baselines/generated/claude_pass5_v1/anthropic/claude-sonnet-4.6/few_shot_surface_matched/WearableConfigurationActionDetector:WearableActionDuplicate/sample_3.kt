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
import com.android.tools.lint.detector.api.Location
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
        private const val WEARABLE_CONFIGURATION_ACTION_META_DATA =
            "com.google.android.wearable.watchface.wearableConfigurationAction"

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                If a watch face service defines `wearableConfigurationAction` metadata with the \
                value `WATCH_FACE_EDITOR`, there should be an activity in the same package which \
                has an intent filter for `WATCH_FACE_EDITOR` \
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
        val mainDocument = context.mainProject.mergedManifest ?: return
        val documentElement = mainDocument.documentElement ?: return

        // Get minSdkVersion
        val minSdkVersion = getMinSdkVersion(documentElement)

        // Find the application element
        val applicationElement = XmlUtils.getFirstSubTagByName(documentElement, TAG_APPLICATION)
            ?: return

        // Collect all services that have wearableConfigurationAction = WATCH_FACE_EDITOR
        val servicesWithWatchFaceEditor = mutableListOf<Element>()
        var serviceTag = XmlUtils.getFirstSubTagByName(applicationElement, TAG_SERVICE)
        while (serviceTag != null) {
            if (serviceHasWatchFaceEditorMetaData(serviceTag)) {
                servicesWithWatchFaceEditor.add(serviceTag)
            }
            serviceTag = XmlUtils.getNextTagByName(serviceTag, TAG_SERVICE)
        }

        if (servicesWithWatchFaceEditor.isEmpty()) {
            return
        }

        // Collect all activities that have an intent filter for WATCH_FACE_EDITOR
        val activitiesWithWatchFaceEditor = mutableListOf<Element>()
        var activityTag = XmlUtils.getFirstSubTagByName(applicationElement, TAG_ACTIVITY)
        while (activityTag != null) {
            if (activityHasWatchFaceEditorAction(activityTag, minSdkVersion)) {
                activitiesWithWatchFaceEditor.add(activityTag)
            }
            activityTag = XmlUtils.getNextTagByName(activityTag, TAG_ACTIVITY)
        }

        // If there are multiple services with wearableConfigurationAction = WATCH_FACE_EDITOR,
        // and multiple activities with WATCH_FACE_EDITOR action, report duplicates
        if (servicesWithWatchFaceEditor.size > 1 || activitiesWithWatchFaceEditor.size > 1) {
            // Report on duplicate activities
            if (activitiesWithWatchFaceEditor.size > 1) {
                for (activity in activitiesWithWatchFaceEditor) {
                    val location = getElementLocation(context, activity)
                    context.report(
                        ISSUE,
                        location,
                        "Duplicate watch face configuration activities found"
                    )
                }
            }

            // Report on duplicate services
            if (servicesWithWatchFaceEditor.size > 1) {
                for (service in servicesWithWatchFaceEditor) {
                    val location = getElementLocation(context, service)
                    context.report(
                        ISSUE,
                        location,
                        "Duplicate watch face configuration activities found"
                    )
                }
            }
        }
    }

    private fun serviceHasWatchFaceEditorMetaData(serviceElement: Element): Boolean {
        var metaData = XmlUtils.getFirstSubTagByName(serviceElement, TAG_META_DATA)
        while (metaData != null) {
            val name = metaData.getAttributeNS(ANDROID_URI, ATTR_NAME)
            val value = metaData.getAttributeNS(ANDROID_URI, ATTR_VALUE)
            if (name == WEARABLE_CONFIGURATION_ACTION_META_DATA &&
                value == WATCH_FACE_EDITOR_ACTION
            ) {
                return true
            }
            metaData = XmlUtils.getNextTagByName(metaData, TAG_META_DATA)
        }
        return false
    }

    private fun activityHasWatchFaceEditorAction(
        activityElement: Element,
        minSdkVersion: Int
    ): Boolean {
        var intentFilter = XmlUtils.getFirstSubTagByName(activityElement, TAG_INTENT_FILTER)
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

    private fun getMinSdkVersion(documentElement: Element): Int {
        val usesSdk = XmlUtils.getFirstSubTagByName(documentElement, TAG_USES_SDK)
            ?: return 1
        val minSdkStr = usesSdk.getAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION)
        return minSdkStr.toIntOrNull() ?: 1
    }

    private fun getElementLocation(context: Context, element: Element): Location {
        val nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)
        return if (nameAttr != null) {
            (context.mainProject.mergedManifest?.let {
                Location.create(context.mainProject.manifestFiles.firstOrNull() ?: context.file)
            }) ?: Location.create(context.file)
        } else {
            Location.create(context.file)
        }
    }
}
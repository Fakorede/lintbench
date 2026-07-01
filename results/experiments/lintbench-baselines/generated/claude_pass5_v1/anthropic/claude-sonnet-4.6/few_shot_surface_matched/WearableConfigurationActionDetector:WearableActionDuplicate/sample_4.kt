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
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.XmlUtils.getFirstSubTagByName
import com.android.utils.XmlUtils.getNextTagByName
import org.w3c.dom.Document
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val WATCH_FACE_EDITOR = "WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_ACTION =
            "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val ACTION_WATCH_FACE_EDITOR =
            "androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR"
        private const val CATEGORY_WEARABLE_CONFIGURATION =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                If a watch face service defines `wearableConfigurationAction` metadata with \
                the value `WATCH_FACE_EDITOR`, there should be exactly one activity in the \
                same package that has an intent filter for `WATCH_FACE_EDITOR`. \
                If `minSdkVersion` is less than 30, the intent filter should also include \
                the `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` \
                category.

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
        val mainProject: Project = context.mainProject
        val mergedManifest: Document = mainProject.mergedManifest ?: return

        val root: Element = mergedManifest.documentElement ?: return

        // Get minSdkVersion
        val minSdkVersion = getMinSdkVersion(root)

        val applicationElement = getFirstSubTagByName(root, TAG_APPLICATION) ?: return

        // Check if any service has wearableConfigurationAction = WATCH_FACE_EDITOR
        val hasWatchFaceEditorService = hasWatchFaceServiceWithEditorAction(applicationElement)
        if (!hasWatchFaceEditorService) {
            return
        }

        // Find all activities with WATCH_FACE_EDITOR intent filter
        val matchingActivities = findActivitiesWithWatchFaceEditorAction(applicationElement, minSdkVersion)

        if (matchingActivities.size > 1) {
            // Report warning: duplicate configuration activities found
            context.report(
                ISSUE,
                context.getLocation(applicationElement),
                "Duplicate watch face configuration activities found: there are " +
                    "${matchingActivities.size} activities with an intent filter for " +
                    "`$ACTION_WATCH_FACE_EDITOR`, but only one is expected."
            )
        }
    }

    private fun getMinSdkVersion(root: Element): Int {
        var usesSdk = getFirstSubTagByName(root, TAG_USES_SDK)
        while (usesSdk != null) {
            val minSdk = usesSdk.getAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION)
            if (minSdk.isNotEmpty()) {
                return minSdk.toIntOrNull() ?: 1
            }
            usesSdk = getNextTagByName(usesSdk, TAG_USES_SDK)
        }
        return 1
    }

    private fun hasWatchFaceServiceWithEditorAction(applicationElement: Element): Boolean {
        var service = getFirstSubTagByName(applicationElement, TAG_SERVICE)
        while (service != null) {
            if (serviceHasWatchFaceEditorMetadata(service)) {
                return true
            }
            service = getNextTagByName(service, TAG_SERVICE)
        }
        return false
    }

    private fun serviceHasWatchFaceEditorMetadata(serviceElement: Element): Boolean {
        var metaData = getFirstSubTagByName(serviceElement, TAG_META_DATA)
        while (metaData != null) {
            val name = metaData.getAttributeNS(ANDROID_URI, ATTR_NAME)
            val value = metaData.getAttributeNS(ANDROID_URI, ATTR_VALUE)
            if (name == WEARABLE_CONFIGURATION_ACTION && value == WATCH_FACE_EDITOR) {
                return true
            }
            metaData = getNextTagByName(metaData, TAG_META_DATA)
        }
        return false
    }

    private fun findActivitiesWithWatchFaceEditorAction(
        applicationElement: Element,
        minSdkVersion: Int
    ): List<Element> {
        val result = mutableListOf<Element>()
        var activity = getFirstSubTagByName(applicationElement, TAG_ACTIVITY)
        while (activity != null) {
            if (activityHasWatchFaceEditorAction(activity, minSdkVersion)) {
                result.add(activity)
            }
            activity = getNextTagByName(activity, TAG_ACTIVITY)
        }
        return result
    }

    private fun activityHasWatchFaceEditorAction(
        activityElement: Element,
        minSdkVersion: Int
    ): Boolean {
        var intentFilter = getFirstSubTagByName(activityElement, TAG_INTENT_FILTER)
        while (intentFilter != null) {
            if (intentFilterMatchesWatchFaceEditor(intentFilter, minSdkVersion)) {
                return true
            }
            intentFilter = getNextTagByName(intentFilter, TAG_INTENT_FILTER)
        }
        return false
    }

    private fun intentFilterMatchesWatchFaceEditor(
        intentFilterElement: Element,
        minSdkVersion: Int
    ): Boolean {
        var hasWatchFaceEditorAction = false
        var hasWearableConfigurationCategory = false

        var action = getFirstSubTagByName(intentFilterElement, TAG_ACTION)
        while (action != null) {
            val actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (actionName == ACTION_WATCH_FACE_EDITOR) {
                hasWatchFaceEditorAction = true
            }
            action = getNextTagByName(action, TAG_ACTION)
        }

        if (!hasWatchFaceEditorAction) {
            return false
        }

        if (minSdkVersion >= 30) {
            // Category not required
            return true
        }

        // Need to also check for WEARABLE_CONFIGURATION category
        var category = getFirstSubTagByName(intentFilterElement, TAG_CATEGORY)
        while (category != null) {
            val categoryName = category.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (categoryName == CATEGORY_WEARABLE_CONFIGURATION) {
                hasWearableConfigurationCategory = true
            }
            category = getNextTagByName(category, TAG_CATEGORY)
        }

        return hasWearableConfigurationCategory
    }
}
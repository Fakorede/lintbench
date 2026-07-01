package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
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
import com.android.utils.XmlUtils.getFirstSubTagByName
import com.android.utils.XmlUtils.getNextTagByName
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val WATCH_FACE_EDITOR = "WATCH_FACE_EDITOR"
        private const val ATTR_VALUE = "value"
        private const val META_DATA_WEARABLE_CONFIGURATION_ACTION = "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val ACTION_WATCH_FACE_EDITOR = "androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR"
        private const val CATEGORY_WEARABLE_CONFIGURATION = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wearable configuration action metadata must match an activity",
            explanation = """
                When a watch face service defines `wearableConfigurationAction` metadata with \
                the value `WATCH_FACE_EDITOR`, there should be an activity in the same package \
                that has an intent filter for `WATCH_FACE_EDITOR`. If `minSdkVersion` is less \
                than 30, the intent filter should also include the \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category.

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
        val mergedManifest = mainProject.mergedManifest ?: return
        val documentElement = mergedManifest.documentElement ?: return

        val minSdkVersion = mainProject.minSdk

        // Collect all activities with WATCH_FACE_EDITOR intent filters
        val activitiesWithEditorAction = mutableSetOf<String>()
        // Also track which ones have the WEARABLE_CONFIGURATION category
        val activitiesWithEditorActionAndCategory = mutableSetOf<String>()

        // Walk through <application> children to find activities
        val applicationElement = getFirstSubTagByName(documentElement, "application") ?: return

        var activityTag = getFirstSubTagByName(applicationElement, TAG_ACTIVITY)
        while (activityTag != null) {
            val activityName = activityTag.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: ""
            if (hasWatchFaceEditorAction(activityTag)) {
                activitiesWithEditorAction.add(activityName)
                if (hasWearableConfigurationCategory(activityTag)) {
                    activitiesWithEditorActionAndCategory.add(activityName)
                }
            }
            activityTag = getNextTagByName(activityTag, TAG_ACTIVITY)
        }

        // Now look for services that define wearableConfigurationAction = WATCH_FACE_EDITOR
        var serviceTag = getFirstSubTagByName(applicationElement, TAG_SERVICE)
        while (serviceTag != null) {
            if (hasWearableConfigurationActionMetadata(serviceTag)) {
                // This service requires a matching activity
                val needsCategory = minSdkVersion < 30

                val hasMatchingActivity = if (needsCategory) {
                    activitiesWithEditorActionAndCategory.isNotEmpty()
                } else {
                    activitiesWithEditorAction.isNotEmpty()
                }

                if (!hasMatchingActivity) {
                    val serviceName = serviceTag.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    val message = buildMessage(needsCategory, serviceName)
                    // Report on the merged manifest location; use the service element location
                    val xmlContext = getXmlContextForMergedManifest(context) ?: run {
                        // Fallback: report on project level
                        context.report(ISSUE, context.getLocation(mergedManifest), message)
                        serviceTag = getNextTagByName(serviceTag, TAG_SERVICE)
                        return@checkMergedProject
                    }
                    xmlContext.report(
                        ISSUE,
                        serviceTag,
                        xmlContext.getLocation(serviceTag),
                        message
                    )
                }
            }
            serviceTag = getNextTagByName(serviceTag, TAG_SERVICE)
        }
    }

    private fun buildMessage(needsCategory: Boolean, serviceName: String?): String {
        val base = "Watch face service `$serviceName` has a `wearableConfigurationAction` " +
            "metadata with value `$WATCH_FACE_EDITOR`, but no matching activity was found " +
            "with an intent filter for `$ACTION_WATCH_FACE_EDITOR`"
        return if (needsCategory) {
            "$base and category `$CATEGORY_WEARABLE_CONFIGURATION` " +
                "(required when minSdkVersion < 30)"
        } else {
            "$base"
        }
    }

    private fun getXmlContextForMergedManifest(context: Context): XmlContext? {
        // We can't get an XmlContext directly from a plain Context for merged manifest,
        // so we return null and callers handle the fallback.
        return null
    }

    private fun hasWearableConfigurationActionMetadata(serviceElement: Element): Boolean {
        var metaData = getFirstSubTagByName(serviceElement, TAG_META_DATA)
        while (metaData != null) {
            val name = metaData.getAttributeNS(ANDROID_URI, ATTR_NAME)
            val value = metaData.getAttributeNS(ANDROID_URI, ATTR_VALUE)
            if (name == META_DATA_WEARABLE_CONFIGURATION_ACTION && value == WATCH_FACE_EDITOR) {
                return true
            }
            metaData = getNextTagByName(metaData, TAG_META_DATA)
        }
        return false
    }

    private fun hasWatchFaceEditorAction(activityElement: Element): Boolean {
        var intentFilter = getFirstSubTagByName(activityElement, TAG_INTENT_FILTER)
        while (intentFilter != null) {
            var action = getFirstSubTagByName(intentFilter, TAG_ACTION)
            while (action != null) {
                val actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (actionName == ACTION_WATCH_FACE_EDITOR) {
                    return true
                }
                action = getNextTagByName(action, TAG_ACTION)
            }
            intentFilter = getNextTagByName(intentFilter, TAG_INTENT_FILTER)
        }
        return false
    }

    private fun hasWearableConfigurationCategory(activityElement: Element): Boolean {
        var intentFilter = getFirstSubTagByName(activityElement, TAG_INTENT_FILTER)
        while (intentFilter != null) {
            // Check if this intent filter has both the action and the category
            var hasEditorAction = false
            var action = getFirstSubTagByName(intentFilter, TAG_ACTION)
            while (action != null) {
                if (action.getAttributeNS(ANDROID_URI, ATTR_NAME) == ACTION_WATCH_FACE_EDITOR) {
                    hasEditorAction = true
                    break
                }
                action = getNextTagByName(action, TAG_ACTION)
            }

            if (hasEditorAction) {
                var category = getFirstSubTagByName(intentFilter, TAG_CATEGORY)
                while (category != null) {
                    if (category.getAttributeNS(ANDROID_URI, ATTR_NAME) == CATEGORY_WEARABLE_CONFIGURATION) {
                        return true
                    }
                    category = getNextTagByName(category, TAG_CATEGORY)
                }
            }
            intentFilter = getNextTagByName(intentFilter, TAG_INTENT_FILTER)
        }
        return false
    }

    // XmlScanner interface — we do all work in checkMergedProject, so no element visitation needed
    override fun getApplicableElements(): Collection<String>? = null
}
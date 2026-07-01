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
import com.android.utils.XmlUtils
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val WATCH_FACE_EDITOR = "WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_ACTION =
            "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val WATCH_FACE_EDITOR_ACTION =
            "androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val WATCH_FACE_CONTROL_SERVICE =
            "com.google.android.wearable.watchface.WatchFaceService"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wearable configuration action metadata must match an activity",
            explanation = """
                When a watch face service defines `wearableConfigurationAction` metadata with \
                the value `WATCH_FACE_EDITOR`, there must be an activity in the same package \
                that has an intent filter for `WATCH_FACE_EDITOR`. If `minSdkVersion` is less \
                than 30, the intent filter must also include the category \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION`.

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
        val mainArtifact = context.project.buildVariant?.mainArtifact
        val mergedManifest = context.project.mergedManifest ?: return
        val root = mergedManifest.documentElement ?: return

        val minSdk = context.project.minSdk

        // Collect all activities that have a WATCH_FACE_EDITOR intent filter action
        // (and optionally the WEARABLE_CONFIGURATION category)
        val activitiesWithEditorAction = mutableSetOf<String>() // package-qualified names
        val activitiesWithEditorActionAndCategory = mutableSetOf<String>()

        val applicationElement = XmlUtils.getFirstSubTagByName(root, "application") ?: return

        // Gather activities
        var activityElement = XmlUtils.getFirstSubTagByName(applicationElement, TAG_ACTIVITY)
        while (activityElement != null) {
            val activityName = activityElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (activityName.isNotEmpty()) {
                var intentFilter = XmlUtils.getFirstSubTagByName(activityElement, TAG_INTENT_FILTER)
                while (intentFilter != null) {
                    var hasEditorAction = false
                    var hasWearableCategory = false

                    var action = XmlUtils.getFirstSubTagByName(intentFilter, TAG_ACTION)
                    while (action != null) {
                        val actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME)
                        if (actionName == WATCH_FACE_EDITOR_ACTION) {
                            hasEditorAction = true
                        }
                        action = XmlUtils.getNextTagByName(action, TAG_ACTION)
                    }

                    var category = XmlUtils.getFirstSubTagByName(intentFilter, TAG_CATEGORY)
                    while (category != null) {
                        val categoryName = category.getAttributeNS(ANDROID_URI, ATTR_NAME)
                        if (categoryName == WEARABLE_CONFIGURATION_CATEGORY) {
                            hasWearableCategory = true
                        }
                        category = XmlUtils.getNextTagByName(category, TAG_CATEGORY)
                    }

                    if (hasEditorAction) {
                        activitiesWithEditorAction.add(activityName)
                        if (hasWearableCategory) {
                            activitiesWithEditorActionAndCategory.add(activityName)
                        }
                    }

                    intentFilter = XmlUtils.getNextTagByName(intentFilter, TAG_INTENT_FILTER)
                }
            }
            activityElement = XmlUtils.getNextTagByName(activityElement, TAG_ACTIVITY)
        }

        // Now check services for wearableConfigurationAction metadata
        var serviceElement = XmlUtils.getFirstSubTagByName(applicationElement, TAG_SERVICE)
        while (serviceElement != null) {
            checkServiceElement(
                context,
                serviceElement,
                minSdk,
                activitiesWithEditorAction,
                activitiesWithEditorActionAndCategory
            )
            serviceElement = XmlUtils.getNextTagByName(serviceElement, TAG_SERVICE)
        }
    }

    private fun checkServiceElement(
        context: Context,
        serviceElement: Element,
        minSdk: Int,
        activitiesWithEditorAction: Set<String>,
        activitiesWithEditorActionAndCategory: Set<String>
    ) {
        // Look for meta-data with name=wearableConfigurationAction and value=WATCH_FACE_EDITOR
        var metaData = XmlUtils.getFirstSubTagByName(serviceElement, TAG_META_DATA)
        while (metaData != null) {
            val metaName = metaData.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (metaName == WEARABLE_CONFIGURATION_ACTION) {
                val metaValue = metaData.getAttributeNS(ANDROID_URI, "value")
                if (metaValue == WATCH_FACE_EDITOR) {
                    // This service declares wearableConfigurationAction = WATCH_FACE_EDITOR
                    // Check that there is a matching activity
                    val requiresCategory = minSdk < 30

                    val hasMatchingActivity = if (requiresCategory) {
                        activitiesWithEditorActionAndCategory.isNotEmpty()
                    } else {
                        activitiesWithEditorAction.isNotEmpty()
                    }

                    if (!hasMatchingActivity) {
                        val message = if (requiresCategory) {
                            "This watch face service defines `wearableConfigurationAction` " +
                                "metadata with value `WATCH_FACE_EDITOR`, but no activity " +
                                "in this package has an intent filter for " +
                                "`$WATCH_FACE_EDITOR_ACTION` with category " +
                                "`$WEARABLE_CONFIGURATION_CATEGORY` (required when " +
                                "`minSdkVersion` < 30)"
                        } else {
                            "This watch face service defines `wearableConfigurationAction` " +
                                "metadata with value `WATCH_FACE_EDITOR`, but no activity " +
                                "in this package has an intent filter for " +
                                "`$WATCH_FACE_EDITOR_ACTION`"
                        }

                        // Report on the meta-data element in the original source if possible,
                        // otherwise fall back to reporting on the project
                        val location = context.getLocation(metaData)
                        context.report(ISSUE, location, message)
                    }
                }
            }
            metaData = XmlUtils.getNextTagByName(metaData, TAG_META_DATA)
        }
    }
}
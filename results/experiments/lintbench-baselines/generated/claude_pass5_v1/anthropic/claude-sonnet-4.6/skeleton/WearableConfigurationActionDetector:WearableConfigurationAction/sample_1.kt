package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.xml.AndroidManifest.NODE_ACTION
import com.android.xml.AndroidManifest.NODE_CATEGORY
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.MANIFEST_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = """
                When a watch face service defines `wearableConfigurationAction` metadata with the \
                value `WATCH_FACE_EDITOR`, there should be an activity in the same package that \
                has an intent filter for `WATCH_FACE_EDITOR`. If `minSdkVersion` is less than 30, \
                the activity must also include the category \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` in its \
                intent filter.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val WATCH_FACE_EDITOR_ACTION = "androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val WEARABLE_CONFIGURATION_ACTION_METADATA = "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val WATCH_FACE_EDITOR_VALUE = "androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR"
    }

    override fun checkMergedProject(context: Context) {
        val mainProject = context.mainProject
        val mergedManifest = mainProject.mergedManifest ?: return
        val root = mergedManifest.documentElement ?: return

        val minSdkVersion = mainProject.minSdk

        // Find the application element
        val applicationElement = root.childNodes.let { nodes ->
            (0 until nodes.length).map { nodes.item(it) }.filterIsInstance<Element>()
                .firstOrNull { it.tagName == TAG_APPLICATION }
        } ?: return

        val children = applicationElement.childNodes
        val elements = (0 until children.length).map { children.item(it) }.filterIsInstance<Element>()

        // Find all services with wearableConfigurationAction metadata set to WATCH_FACE_EDITOR
        val servicesWithMetadata = elements
            .filter { it.tagName == TAG_SERVICE }
            .filter { serviceElement ->
                hasWearableConfigurationActionMetadata(serviceElement)
            }

        if (servicesWithMetadata.isEmpty()) return

        // Find all activities with WATCH_FACE_EDITOR intent filter
        val activities = elements.filter { it.tagName == TAG_ACTIVITY }

        val activitiesWithWatchFaceEditor = activities.filter { activity ->
            hasWatchFaceEditorAction(activity)
        }

        val activitiesWithWatchFaceEditorAndCategory = activities.filter { activity ->
            hasWatchFaceEditorAction(activity) && hasWearableConfigurationCategory(activity)
        }

        for (serviceElement in servicesWithMetadata) {
            if (minSdkVersion < 30) {
                // Need both the action and the category
                if (activitiesWithWatchFaceEditorAndCategory.isEmpty()) {
                    // Check if we have the action but missing category
                    if (activitiesWithWatchFaceEditor.isNotEmpty()) {
                        // Activity exists with action but missing category
                        val location = context.getLocation(serviceElement)
                        context.report(
                            ISSUE,
                            location,
                            "This service defines `wearableConfigurationAction` metadata, but " +
                                "no activity in the package has both an intent filter for " +
                                "`$WATCH_FACE_EDITOR_ACTION` and the category " +
                                "`$WEARABLE_CONFIGURATION_CATEGORY` (required when minSdkVersion < 30)"
                        )
                    } else {
                        // No matching activity at all
                        val location = context.getLocation(serviceElement)
                        context.report(
                            ISSUE,
                            location,
                            "This service defines `wearableConfigurationAction` metadata, but " +
                                "no activity in the package has an intent filter for " +
                                "`$WATCH_FACE_EDITOR_ACTION` with category " +
                                "`$WEARABLE_CONFIGURATION_CATEGORY` (required when minSdkVersion < 30)"
                        )
                    }
                }
            } else {
                // Only need the action
                if (activitiesWithWatchFaceEditor.isEmpty()) {
                    val location = context.getLocation(serviceElement)
                    context.report(
                        ISSUE,
                        location,
                        "This service defines `wearableConfigurationAction` metadata, but " +
                            "no activity in the package has an intent filter for " +
                            "`$WATCH_FACE_EDITOR_ACTION`"
                    )
                }
            }
        }

        // Also check: if there's an activity with WATCH_FACE_EDITOR but no service with metadata
        if (servicesWithMetadata.isEmpty()) {
            // No services with metadata, nothing to check from this direction
            return
        }
    }

    private fun hasWearableConfigurationActionMetadata(serviceElement: Element): Boolean {
        val children = serviceElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.tagName == TAG_META_DATA) {
                val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = child.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (name == WEARABLE_CONFIGURATION_ACTION_METADATA &&
                    value == WATCH_FACE_EDITOR_VALUE
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun hasWatchFaceEditorAction(activityElement: Element): Boolean {
        val children = activityElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.tagName == TAG_INTENT_FILTER) {
                val filterChildren = child.childNodes
                for (j in 0 until filterChildren.length) {
                    val filterChild = filterChildren.item(j) as? Element ?: continue
                    if (filterChild.tagName == NODE_ACTION) {
                        val name = filterChild.getAttributeNS(ANDROID_URI, ATTR_NAME)
                        if (name == WATCH_FACE_EDITOR_ACTION) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    private fun hasWearableConfigurationCategory(activityElement: Element): Boolean {
        val children = activityElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.tagName == TAG_INTENT_FILTER) {
                val filterChildren = child.childNodes
                var hasAction = false
                var hasCategory = false
                for (j in 0 until filterChildren.length) {
                    val filterChild = filterChildren.item(j) as? Element ?: continue
                    when (filterChild.tagName) {
                        NODE_ACTION -> {
                            val name = filterChild.getAttributeNS(ANDROID_URI, ATTR_NAME)
                            if (name == WATCH_FACE_EDITOR_ACTION) {
                                hasAction = true
                            }
                        }
                        NODE_CATEGORY -> {
                            val name = filterChild.getAttributeNS(ANDROID_URI, ATTR_NAME)
                            if (name == WEARABLE_CONFIGURATION_CATEGORY) {
                                hasCategory = true
                            }
                        }
                    }
                }
                if (hasAction && hasCategory) {
                    return true
                }
            }
        }
        return false
    }
}
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
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.xml.AndroidManifest.NODE_ACTION
import com.android.xml.AndroidManifest.NODE_CATEGORY
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val WATCH_FACE_EDITOR_ACTION =
            "androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val WEARABLE_CONFIGURATION_ACTION_META_DATA =
            "com.google.android.wearable.watchface.wearableConfigurationAction"

        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.MANIFEST_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                If a watch face service defines `wearableConfigurationAction` metadata with the \
                value `WATCH_FACE_EDITOR`, there should be an activity in the same package which \
                has an intent filter for `WATCH_FACE_EDITOR` \
                (with `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` \
                if `minSdkVersion` is less than 30).
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private fun getAndroidAttr(element: Element, attr: String): String? {
            return element.getAttributeNS(ANDROID_URI, attr).takeIf { it.isNotEmpty() }
        }

        private fun getChildElements(element: Element, tagName: String): List<Element> {
            val result = mutableListOf<Element>()
            val children = element.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child is Element && child.tagName == tagName) {
                    result.add(child)
                }
            }
            return result
        }

        private fun getAllChildElements(element: Element): List<Element> {
            val result = mutableListOf<Element>()
            val children = element.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child is Element) {
                    result.add(child)
                }
            }
            return result
        }
    }

    override fun checkMergedProject(context: Context) {
        val mainArtifact = context.project.buildVariant?.mainArtifact ?: return
        val manifest = context.mainProject.mergedManifest ?: return

        val root = manifest.documentElement ?: return
        val applicationElements = getChildElements(root, TAG_APPLICATION)
        if (applicationElements.isEmpty()) return

        val application = applicationElements.first()

        // Find all services that have the wearableConfigurationAction meta-data with WATCH_FACE_EDITOR value
        val services = getChildElements(application, TAG_SERVICE)
        var hasWatchFaceEditorMetaData = false

        for (service in services) {
            val metaDataElements = getChildElements(service, TAG_META_DATA)
            for (metaData in metaDataElements) {
                val name = getAndroidAttr(metaData, ATTR_NAME)
                val value = getAndroidAttr(metaData, ATTR_VALUE)
                if (name == WEARABLE_CONFIGURATION_ACTION_META_DATA &&
                    value == WATCH_FACE_EDITOR_ACTION
                ) {
                    hasWatchFaceEditorMetaData = true
                    break
                }
            }
            if (hasWatchFaceEditorMetaData) break
        }

        if (!hasWatchFaceEditorMetaData) return

        // Now check activities for a matching intent filter
        val activities = getChildElements(application, TAG_ACTIVITY)
        val minSdkVersion = context.mainProject.minSdkVersion.apiLevel

        var hasMatchingActivity = false

        for (activity in activities) {
            val intentFilters = getChildElements(activity, TAG_INTENT_FILTER)
            for (intentFilter in intentFilters) {
                val actions = getChildElements(intentFilter, NODE_ACTION)
                val hasWatchFaceEditorAction = actions.any { action ->
                    getAndroidAttr(action, ATTR_NAME) == WATCH_FACE_EDITOR_ACTION
                }

                if (!hasWatchFaceEditorAction) continue

                if (minSdkVersion >= 30) {
                    // No need to check for category
                    hasMatchingActivity = true
                    break
                } else {
                    // Need WEARABLE_CONFIGURATION category as well
                    val categories = getChildElements(intentFilter, NODE_CATEGORY)
                    val hasWearableConfigCategory = categories.any { category ->
                        getAndroidAttr(category, ATTR_NAME) == WEARABLE_CONFIGURATION_CATEGORY
                    }
                    if (hasWearableConfigCategory) {
                        hasMatchingActivity = true
                        break
                    }
                }
            }
            if (hasMatchingActivity) break
        }

        if (!hasMatchingActivity) {
            val location = context.getLocation(application)
            val message = if (minSdkVersion < 30) {
                "Watch face has a `wearableConfigurationAction` metadata with value " +
                    "`WATCH_FACE_EDITOR`, but no activity was found with an intent filter for " +
                    "`$WATCH_FACE_EDITOR_ACTION` and category " +
                    "`$WEARABLE_CONFIGURATION_CATEGORY`"
            } else {
                "Watch face has a `wearableConfigurationAction` metadata with value " +
                    "`WATCH_FACE_EDITOR`, but no activity was found with an intent filter for " +
                    "`$WATCH_FACE_EDITOR_ACTION`"
            }
            context.report(ISSUE, location, message)
        }
    }
}
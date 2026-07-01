package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_SERVICE
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
import java.util.EnumSet

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val WATCH_FACE_EDITOR_ACTION = "androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val WEARABLE_CONFIGURATION_ACTION_METADATA = "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val TAG_META_DATA = "meta-data"
        private const val TAG_CATEGORY = "category"
        private const val TAG_ACTION = "action"

        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                If a watch face service defines `wearableConfigurationAction` metadata with the value \
                `WATCH_FACE_EDITOR`, there should be an activity in the same package which has an \
                intent filter for `WATCH_FACE_EDITOR` (with \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` if \
                minSdkVersion is less than 30).
                
                See https://developer.android.com/training/wearables/watch-faces/configuration
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                EnumSet.of(Scope.MANIFEST)
            )
        ).addMoreInfo("https://developer.android.com/training/wearables/watch-faces/configuration")
    }

    // Data collected during manifest scanning
    private data class ServiceInfo(
        val element: Element,
        val location: Location,
        val packageName: String
    )

    private data class ActivityInfo(
        val element: Element,
        val location: Location,
        val packageName: String,
        val hasWatchFaceEditorAction: Boolean,
        val hasWearableConfigurationCategory: Boolean
    )

    private val servicesWithWatchFaceEditor = mutableListOf<ServiceInfo>()
    private val activitiesWithWatchFaceEditor = mutableListOf<ActivityInfo>()

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_SERVICE, TAG_ACTIVITY)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val manifestPackage = getManifestPackage(context)

        when (element.tagName) {
            TAG_SERVICE -> {
                if (serviceHasWatchFaceEditorMetadata(element)) {
                    servicesWithWatchFaceEditor.add(
                        ServiceInfo(
                            element = element,
                            location = context.getLocation(element),
                            packageName = manifestPackage
                        )
                    )
                }
            }
            TAG_ACTIVITY -> {
                val (hasAction, hasCategory) = activityIntentFilterInfo(element)
                if (hasAction) {
                    activitiesWithWatchFaceEditor.add(
                        ActivityInfo(
                            element = element,
                            location = context.getLocation(element),
                            packageName = manifestPackage,
                            hasWatchFaceEditorAction = hasAction,
                            hasWearableConfigurationCategory = hasCategory
                        )
                    )
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        if (context !is XmlContext) return

        val minSdkVersion = context.project.minSdkVersion.apiLevel
        val requiresCategory = minSdkVersion < 30

        for (serviceInfo in servicesWithWatchFaceEditor) {
            val matchingActivities = activitiesWithWatchFaceEditor.filter {
                it.packageName == serviceInfo.packageName
            }

            if (matchingActivities.isEmpty()) {
                // No matching activity found - report on the service
                context.report(
                    issue = ISSUE,
                    location = serviceInfo.location,
                    message = "Watch face service defines `wearableConfigurationAction` with " +
                            "`WATCH_FACE_EDITOR` but no matching activity with an intent filter " +
                            "for `$WATCH_FACE_EDITOR_ACTION` was found in the same package"
                )
            } else if (requiresCategory) {
                // Check that at least one matching activity has the WEARABLE_CONFIGURATION category
                val hasActivityWithCategory = matchingActivities.any { it.hasWearableConfigurationCategory }
                if (!hasActivityWithCategory) {
                    for (activity in matchingActivities) {
                        context.report(
                            issue = ISSUE,
                            location = activity.location,
                            message = "Activity with `$WATCH_FACE_EDITOR_ACTION` intent filter " +
                                    "should also include the " +
                                    "`$WEARABLE_CONFIGURATION_CATEGORY` category " +
                                    "when minSdkVersion is less than 30"
                        )
                    }
                }
            }
        }

        // Check for activities with WATCH_FACE_EDITOR but no corresponding service
        if (servicesWithWatchFaceEditor.isEmpty() && activitiesWithWatchFaceEditor.isNotEmpty()) {
            for (activityInfo in activitiesWithWatchFaceEditor) {
                context.report(
                    issue = ISSUE,
                    location = activityInfo.location,
                    message = "Activity has intent filter for `$WATCH_FACE_EDITOR_ACTION` but " +
                            "no watch face service with `wearableConfigurationAction` metadata " +
                            "set to `WATCH_FACE_EDITOR` was found"
                )
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        // Clear state after checking is complete
        servicesWithWatchFaceEditor.clear()
        activitiesWithWatchFaceEditor.clear()
    }

    private fun serviceHasWatchFaceEditorMetadata(serviceElement: Element): Boolean {
        val children = serviceElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == TAG_META_DATA) {
                val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = child.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (name == WEARABLE_CONFIGURATION_ACTION_METADATA &&
                    value == WATCH_FACE_EDITOR_ACTION) {
                    return true
                }
            }
        }
        return false
    }

    private fun activityIntentFilterInfo(activityElement: Element): Pair<Boolean, Boolean> {
        var hasWatchFaceEditorAction = false
        var hasWearableConfigurationCategory = false

        val children = activityElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == TAG_INTENT_FILTER) {
                var filterHasAction = false
                var filterHasCategory = false

                val filterChildren = child.childNodes
                for (j in 0 until filterChildren.length) {
                    val filterChild = filterChildren.item(j)
                    if (filterChild is Element) {
                        when (filterChild.tagName) {
                            TAG_ACTION -> {
                                val actionName = filterChild.getAttributeNS(ANDROID_URI, ATTR_NAME)
                                if (actionName == WATCH_FACE_EDITOR_ACTION) {
                                    filterHasAction = true
                                }
                            }
                            TAG_CATEGORY -> {
                                val categoryName = filterChild.getAttributeNS(ANDROID_URI, ATTR_NAME)
                                if (categoryName == WEARABLE_CONFIGURATION_CATEGORY) {
                                    filterHasCategory = true
                                }
                            }
                        }
                    }
                }

                if (filterHasAction) {
                    hasWatchFaceEditorAction = true
                    if (filterHasCategory) {
                        hasWearableConfigurationCategory = true
                    }
                }
            }
        }

        return Pair(hasWatchFaceEditorAction, hasWearableConfigurationCategory)
    }

    private fun getManifestPackage(context: XmlContext): String {
        val document = context.document
        val manifestElement = document.documentElement
        return manifestElement?.getAttribute("package") ?: context.project.`package` ?: ""
    }
}
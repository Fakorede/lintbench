package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_MIN_SDK_VERSION
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_INTENT_FILTER
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
import org.w3c.dom.Element
import org.w3c.dom.Node

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val WATCH_FACE_EDITOR_ACTION =
            "com.google.android.wearable.watchface.editor.action.WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val META_DATA_TAG = "meta-data"
        private const val WEARABLE_CONFIGURATION_ACTION_NAME =
            "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val TAG_ACTION = "action"
        private const val TAG_CATEGORY = "category"

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wearable configuration action metadata must match an activity",
            explanation = """
                When a watch face service defines `wearableConfigurationAction` metadata with the \
                value `WATCH_FACE_EDITOR`, there must be an activity in the same package that has \
                an intent filter for `WATCH_FACE_EDITOR`. If `minSdkVersion` is less than 30, the \
                intent filter must also include the \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category.
                
                See https://developer.android.com/training/wearables/watch-faces/configuration
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    // Data collected during manifest scanning
    private data class ServiceInfo(
        val element: Element,
        val location: Location,
        val packageName: String
    )

    private data class ActivityInfo(
        val element: Element,
        val packageName: String,
        val hasWatchFaceEditorAction: Boolean,
        val hasWearableConfigurationCategory: Boolean
    )

    private val servicesWithConfigAction = mutableListOf<ServiceInfo>()
    private val activitiesWithEditorAction = mutableListOf<ActivityInfo>()
    private var minSdkVersion: Int = 1

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_SERVICE, TAG_ACTIVITY, TAG_USES_SDK)
    }

    override fun beforeCheckFile(context: Context) {
        servicesWithConfigAction.clear()
        activitiesWithEditorAction.clear()
        minSdkVersion = 1
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_USES_SDK -> {
                val minSdk = element.getAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION)
                if (minSdk.isNotEmpty()) {
                    minSdkVersion = minSdk.toIntOrNull() ?: 1
                }
            }
            TAG_SERVICE -> {
                if (hasWearableConfigurationActionMetadata(element)) {
                    val packageName = getPackageName(context, element)
                    servicesWithConfigAction.add(
                        ServiceInfo(
                            element = element,
                            location = context.getLocation(element),
                            packageName = packageName
                        )
                    )
                }
            }
            TAG_ACTIVITY -> {
                val packageName = getPackageName(context, element)
                val (hasAction, hasCategory) = getIntentFilterInfo(element)
                if (hasAction) {
                    activitiesWithEditorAction.add(
                        ActivityInfo(
                            element = element,
                            packageName = packageName,
                            hasWatchFaceEditorAction = hasAction,
                            hasWearableConfigurationCategory = hasCategory
                        )
                    )
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        if (servicesWithConfigAction.isEmpty()) return

        for (serviceInfo in servicesWithConfigAction) {
            val matchingActivities = activitiesWithEditorAction.filter { activity ->
                activity.packageName == serviceInfo.packageName
            }

            if (matchingActivities.isEmpty()) {
                // No activity with WATCH_FACE_EDITOR action found
                context.report(
                    issue = ISSUE,
                    location = serviceInfo.location,
                    message = "Watch face service defines `wearableConfigurationAction` metadata " +
                            "with value `WATCH_FACE_EDITOR`, but no activity in the same package " +
                            "has an intent filter for `$WATCH_FACE_EDITOR_ACTION`"
                )
            } else if (minSdkVersion < 30) {
                // Check that at least one activity also has the WEARABLE_CONFIGURATION category
                val hasCategory = matchingActivities.any { it.hasWearableConfigurationCategory }
                if (!hasCategory) {
                    context.report(
                        issue = ISSUE,
                        location = serviceInfo.location,
                        message = "Watch face service defines `wearableConfigurationAction` " +
                                "metadata with value `WATCH_FACE_EDITOR`, and `minSdkVersion` " +
                                "is less than 30, so the activity's intent filter must also " +
                                "include the `$WEARABLE_CONFIGURATION_CATEGORY` category"
                    )
                }
            }
        }
    }

    private fun hasWearableConfigurationActionMetadata(serviceElement: Element): Boolean {
        val children = serviceElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == META_DATA_TAG) {
                val metaDataElement = child as Element
                val name = metaDataElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = metaDataElement.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (name == WEARABLE_CONFIGURATION_ACTION_NAME &&
                    value == WATCH_FACE_EDITOR_ACTION
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun getIntentFilterInfo(activityElement: Element): Pair<Boolean, Boolean> {
        var hasWatchFaceEditorAction = false
        var hasWearableConfigurationCategory = false

        val children = activityElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == TAG_INTENT_FILTER) {
                val intentFilter = child as Element
                val filterChildren = intentFilter.childNodes
                var filterHasAction = false
                var filterHasCategory = false

                for (j in 0 until filterChildren.length) {
                    val filterChild = filterChildren.item(j)
                    if (filterChild.nodeType == Node.ELEMENT_NODE) {
                        val filterChildElement = filterChild as Element
                        when (filterChildElement.tagName) {
                            TAG_ACTION -> {
                                val actionName =
                                    filterChildElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
                                if (actionName == WATCH_FACE_EDITOR_ACTION) {
                                    filterHasAction = true
                                }
                            }
                            TAG_CATEGORY -> {
                                val categoryName =
                                    filterChildElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
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

    private fun getPackageName(context: XmlContext, element: Element): String {
        // Try to get the fully qualified name from the android:name attribute
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        val manifestPackage = getManifestPackage(context)

        return when {
            name.startsWith(".") -> "$manifestPackage$name"
            name.contains(".") -> name
            else -> "$manifestPackage.$name"
        }.substringBeforeLast(".")
    }

    private fun getManifestPackage(context: XmlContext): String {
        val document = context.document
        val manifestElement = document.documentElement
        return manifestElement.getAttribute("package") ?: ""
    }
}
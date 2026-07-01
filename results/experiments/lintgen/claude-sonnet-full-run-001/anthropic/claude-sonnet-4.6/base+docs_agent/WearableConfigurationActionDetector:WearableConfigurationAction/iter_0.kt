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
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val WATCH_FACE_EDITOR_ACTION =
            "androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val WEARABLE_CONFIGURATION_ACTION_METADATA =
            "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val TAG_META_DATA = "meta-data"
        private const val TAG_ACTION = "action"
        private const val TAG_CATEGORY = "category"

        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wearable configuration action metadata must match an activity",
            explanation = """
                When a watch face service defines `wearableConfigurationAction` metadata with the \
                value `WATCH_FACE_EDITOR`, there must be an activity in the same package that has \
                an intent filter for `WATCH_FACE_EDITOR`. If `minSdkVersion` is less than 30, the \
                intent filter must also include the \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category.
                
                See https://developer.android.com/training/wearables/watch-faces/configuration for \
                more details.
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

    // Data collected during manifest analysis
    private data class ServiceInfo(
        val element: Element,
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
    private var manifestPackage: String = ""

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_SERVICE, TAG_ACTIVITY, TAG_USES_SDK)
    }

    override fun beforeCheckFile(context: Context) {
        servicesWithConfigAction.clear()
        activitiesWithEditorAction.clear()
        minSdkVersion = 1
        manifestPackage = ""
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
                    val pkg = getPackageName(context, element)
                    servicesWithConfigAction.add(ServiceInfo(element, pkg))
                }
            }
            TAG_ACTIVITY -> {
                val (hasAction, hasCategory) = getActivityIntentFilterInfo(element)
                if (hasAction) {
                    val pkg = getPackageName(context, element)
                    activitiesWithEditorAction.add(
                        ActivityInfo(element, pkg, hasAction, hasCategory)
                    )
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        // Get the manifest package
        val document = context.document ?: return
        val manifestElement = document.documentElement ?: return
        manifestPackage = manifestElement.getAttribute("package") ?: ""

        for (serviceInfo in servicesWithConfigAction) {
            val matchingActivity = activitiesWithEditorAction.find { activityInfo ->
                activityInfo.packageName == serviceInfo.packageName
            }

            if (matchingActivity == null) {
                // No matching activity found at all
                context.report(
                    ISSUE,
                    serviceInfo.element,
                    context.getLocation(serviceInfo.element),
                    "Watch face service defines `wearableConfigurationAction` metadata with " +
                        "`WATCH_FACE_EDITOR` value, but no activity in the same package has " +
                        "an intent filter for `$WATCH_FACE_EDITOR_ACTION`"
                )
            } else if (minSdkVersion < 30 && !matchingActivity.hasWearableConfigurationCategory) {
                // Activity found but missing the required category for minSdkVersion < 30
                context.report(
                    ISSUE,
                    matchingActivity.element,
                    context.getLocation(matchingActivity.element),
                    "Activity with `$WATCH_FACE_EDITOR_ACTION` intent filter must also include " +
                        "the `$WEARABLE_CONFIGURATION_CATEGORY` category when `minSdkVersion` " +
                        "is less than 30"
                )
            }
        }
    }

    private fun hasWearableConfigurationActionMetadata(serviceElement: Element): Boolean {
        val children = serviceElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == TAG_META_DATA) {
                val metaElement = child as Element
                val name = metaElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = metaElement.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (name == WEARABLE_CONFIGURATION_ACTION_METADATA &&
                    value == WATCH_FACE_EDITOR_ACTION
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun getActivityIntentFilterInfo(activityElement: Element): Pair<Boolean, Boolean> {
        var hasWatchFaceEditorAction = false
        var hasWearableConfigurationCategory = false

        val children = activityElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == TAG_INTENT_FILTER) {
                val intentFilter = child as Element
                var filterHasAction = false
                var filterHasCategory = false

                val filterChildren = intentFilter.childNodes
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
        // Check if the element has an explicit package in its name
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: ""
        return if (name.contains('.') && !name.startsWith('.')) {
            // Fully qualified name - extract package
            val lastDot = name.lastIndexOf('.')
            name.substring(0, lastDot)
        } else {
            // Relative name - use manifest package
            val document = context.document
            val manifestElement = document?.documentElement
            manifestElement?.getAttribute("package") ?: manifestPackage
        }
    }
}
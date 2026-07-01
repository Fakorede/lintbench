package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_MIN_SDK_VERSION
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.SdkConstants.TAG_USES_SDK
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LocationHandle
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.xml.AndroidManifest
import org.w3c.dom.Element
import com.android.tools.lint.detector.api.ManifestDetector
import java.util.EnumSet

class WearableConfigurationActionDetector : ManifestDetector(), XmlScanner {

    companion object {
        private const val WATCH_FACE_EDITOR_ACTION =
            "androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val WEARABLE_CONFIGURATION_ACTION_NAME =
            "com.google.android.wearable.watchface.wearableConfigurationAction"

        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wearable configuration action metadata must match an activity",
            explanation = """
                Only when a watch face service defines `wearableConfigurationAction` metadata, \
                with the value `WATCH_FACE_EDITOR`, there should be an activity in the same \
                package, which has an intent filter for `WATCH_FACE_EDITOR` \
                (with `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` \
                if `minSdkVersion` is less than 30).
                
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

    // Data class to hold information about a service with wearableConfigurationAction metadata
    private data class ServiceWithConfigAction(
        val element: Element,
        val locationHandle: LocationHandle
    )

    // Data class to hold information about an activity with WATCH_FACE_EDITOR intent filter
    private data class ActivityWithEditorAction(
        val hasWearableConfigCategory: Boolean
    )

    private var minSdkVersion: Int = 1

    // Services that declare wearableConfigurationAction = WATCH_FACE_EDITOR
    private val servicesWithConfigAction = mutableListOf<ServiceWithConfigAction>()

    // Whether we found an activity with the WATCH_FACE_EDITOR action
    private var foundEditorActivity: Boolean = false
    private var foundEditorActivityWithCategory: Boolean = false

    override fun getApplicableElements(): Collection<String> = listOf(
        TAG_USES_SDK,
        TAG_SERVICE,
        TAG_ACTIVITY
    )

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_USES_SDK -> {
                val minSdk = element.getAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION)
                if (minSdk.isNotEmpty()) {
                    minSdkVersion = minSdk.toIntOrNull() ?: 1
                }
            }
            TAG_SERVICE -> {
                checkService(context, element)
            }
            TAG_ACTIVITY -> {
                checkActivity(context, element)
            }
        }
    }

    private fun checkService(context: XmlContext, element: Element) {
        // Look for meta-data children with wearableConfigurationAction = WATCH_FACE_EDITOR
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.tagName == TAG_META_DATA) {
                val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = child.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (name == WEARABLE_CONFIGURATION_ACTION_NAME &&
                    value == WATCH_FACE_EDITOR_ACTION
                ) {
                    val handle = context.createLocationHandle(element)
                    handle.clientData = element
                    servicesWithConfigAction.add(
                        ServiceWithConfigAction(element, handle)
                    )
                    break
                }
            }
        }
    }

    private fun checkActivity(context: XmlContext, element: Element) {
        // Look for intent-filter children with action WATCH_FACE_EDITOR
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.tagName == TAG_INTENT_FILTER) {
                var hasEditorAction = false
                var hasWearableCategory = false

                val filterChildren = child.childNodes
                for (j in 0 until filterChildren.length) {
                    val filterChild = filterChildren.item(j) as? Element ?: continue
                    when (filterChild.tagName) {
                        TAG_ACTION -> {
                            val actionName = filterChild.getAttributeNS(ANDROID_URI, ATTR_NAME)
                            if (actionName == WATCH_FACE_EDITOR_ACTION) {
                                hasEditorAction = true
                            }
                        }
                        TAG_CATEGORY -> {
                            val categoryName = filterChild.getAttributeNS(ANDROID_URI, ATTR_NAME)
                            if (categoryName == WEARABLE_CONFIGURATION_CATEGORY) {
                                hasWearableCategory = true
                            }
                        }
                    }
                }

                if (hasEditorAction) {
                    foundEditorActivity = true
                    if (hasWearableCategory) {
                        foundEditorActivityWithCategory = true
                    }
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        if (servicesWithConfigAction.isEmpty()) {
            return
        }

        // If there are services with wearableConfigurationAction = WATCH_FACE_EDITOR,
        // check that there's a matching activity
        for (service in servicesWithConfigAction) {
            if (!foundEditorActivity) {
                // No activity with WATCH_FACE_EDITOR action at all
                val location = service.locationHandle.resolve()
                context.report(
                    ISSUE,
                    location,
                    "Watch face configuration activity must have an intent filter with action " +
                            "`$WATCH_FACE_EDITOR_ACTION`"
                )
            } else if (minSdkVersion < 30 && !foundEditorActivityWithCategory) {
                // Activity exists but missing WEARABLE_CONFIGURATION category (required for API < 30)
                val location = service.locationHandle.resolve()
                context.report(
                    ISSUE,
                    location,
                    "Watch face configuration activity must have an intent filter with category " +
                            "`$WEARABLE_CONFIGURATION_CATEGORY` when `minSdkVersion` is less than 30"
                )
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        // Reset state for next project
        servicesWithConfigAction.clear()
        foundEditorActivity = false
        foundEditorActivityWithCategory = false
        minSdkVersion = 1
    }
}
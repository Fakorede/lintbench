package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), Detector.XmlScanner {

    private val servicesNeedingEditor = mutableListOf<ServiceConfig>()
    private val editorIntentFilters = mutableListOf<FilterInfo>()

    override fun getApplicableElements(): Collection<String> =
        listOf(
            SdkConstants.TAG_SERVICE,
            SdkConstants.TAG_ACTIVITY,
            SdkConstants.TAG_ACTIVITY_ALIAS
        )

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            SdkConstants.TAG_SERVICE -> checkService(context, element)
            SdkConstants.TAG_ACTIVITY,
            SdkConstants.TAG_ACTIVITY_ALIAS -> checkActivity(context, element)
        }
    }

    override fun beforeCheckEachProject(context: Context) {
        servicesNeedingEditor.clear()
        editorIntentFilters.clear()
    }

    override fun afterCheckEachProject(context: Context) {
        if (servicesNeedingEditor.isEmpty()) {
            return
        }

        val requiresCategory = context.project.minSdkVersion.apiLevel < 30
        for (service in servicesNeedingEditor) {
            val hasMatchingActivity = editorIntentFilters.any {
                it.hasAction && (!requiresCategory || it.hasCategory)
            }
            if (!hasMatchingActivity) {
                context.report(
                    ISSUE,
                    service.location,
                    buildErrorMessage(requiresCategory)
                )
            }
        }
    }

    private fun checkService(context: XmlContext, service: Element) {
        val children = service.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.tagName != SdkConstants.TAG_META_DATA) {
                continue
            }

            val name = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (name != WEARABLE_CONFIGURATION_ACTION) {
                continue
            }

            val value = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE)
            if (value == WATCH_FACE_EDITOR) {
                servicesNeedingEditor.add(ServiceConfig(context.getLocation(child)))
            }
        }
    }

    private fun checkActivity(context: XmlContext, activity: Element) {
        val children = activity.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.tagName != SdkConstants.TAG_INTENT_FILTER) {
                continue
            }

            val hasAction = child.hasChildWithName(
                SdkConstants.TAG_ACTION,
                SdkConstants.ATTR_NAME,
                WATCH_FACE_EDITOR
            )
            val hasCategory = child.hasChildWithName(
                SdkConstants.TAG_CATEGORY,
                SdkConstants.ATTR_NAME,
                WEARABLE_CONFIGURATION_CATEGORY
            )

            if (hasAction || hasCategory) {
                editorIntentFilters.add(FilterInfo(hasAction, hasCategory))
            }
        }
    }

    private fun Element.hasChildWithName(
        tag: String,
        nameAttribute: String,
        expectedName: String
    ): Boolean {
        val children = childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.tagName == tag) {
                val name = child.getAttributeNS(SdkConstants.ANDROID_URI, nameAttribute)
                if (name == expectedName) {
                    return true
                }
            }
        }
        return false
    }

    private fun buildErrorMessage(requiresCategory: Boolean): String {
        return if (requiresCategory) {
            "When a watch face service declares wearableConfigurationAction with value WATCH_FACE_EDITOR, " +
                "an activity in the same package must have an intent filter with action WATCH_FACE_EDITOR " +
                "and category $WEARABLE_CONFIGURATION_CATEGORY"
        } else {
            "When a watch face service declares wearableConfigurationAction with value WATCH_FACE_EDITOR, " +
                "an activity in the same package must have an intent filter with action WATCH_FACE_EDITOR"
        }
    }

    private data class ServiceConfig(val location: Location)
    private data class FilterInfo(val hasAction: Boolean, val hasCategory: Boolean)

    companion object {
        private const val WEARABLE_CONFIGURATION_ACTION =
            "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val WATCH_FACE_EDITOR = "WATCH_FACE_EDITOR"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Missing activity for watch face configuration action",
            explanation = """
                When a watch face service declares the metadata
                com.google.android.wearable.watchface.wearableConfigurationAction
                with the value WATCH_FACE_EDITOR, there must be a matching activity
                with an intent filter for that action. If minSdkVersion is less than
                30, the activity must also include the category
                com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION.
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
}
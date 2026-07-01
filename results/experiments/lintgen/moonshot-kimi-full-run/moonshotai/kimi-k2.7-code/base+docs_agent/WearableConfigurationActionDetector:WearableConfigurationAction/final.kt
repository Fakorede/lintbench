package com.android.tools.lint.checks

import com.android.SdkConstants
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

    private val serviceConfigs = mutableListOf<ServiceConfig>()
    private val activityFilters = mutableListOf<ActivityFilter>()

    override fun getApplicableElements(): List<String> =
        listOf(
            SdkConstants.TAG_SERVICE,
            SdkConstants.TAG_ACTIVITY,
            SdkConstants.TAG_ACTIVITY_ALIAS
        )

    override fun beforeCheckRootProject(context: Context) {
        serviceConfigs.clear()
        activityFilters.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val pkg = context.document.documentElement?.getAttribute(SdkConstants.ATTR_PACKAGE) ?: return
        when (element.tagName) {
            SdkConstants.TAG_SERVICE -> visitService(context, element, pkg)
            SdkConstants.TAG_ACTIVITY,
            SdkConstants.TAG_ACTIVITY_ALIAS -> visitActivity(context, element, pkg)
        }
    }

    private fun visitService(context: XmlContext, service: Element, pkg: String) {
        val minSdk = context.mainProject.minSdk
        val children = service.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val child = node as Element
            if (child.tagName != SdkConstants.TAG_META_DATA) continue
            val name = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (name !in WEARABLE_CONFIGURATION_METADATA_NAMES) continue
            val value = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE)
            if (value.isBlank() || value.startsWith("@")) continue
            serviceConfigs.add(ServiceConfig(context, child, value, minSdk, pkg))
        }
    }

    private fun visitActivity(context: XmlContext, activity: Element, pkg: String) {
        val filters = activity.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER)
        for (i in 0 until filters.length) {
            val filter = filters.item(i) as? Element ?: continue
            val actions = filter.actions()
            if (actions.isEmpty()) continue
            activityFilters.add(
                ActivityFilter(
                    filter,
                    actions,
                    filter.hasCategory(WEARABLE_CONFIGURATION_CATEGORY),
                    pkg,
                    context
                )
            )
        }
    }

    override fun afterCheckRootProject(context: Context) {
        for (service in serviceConfigs) {
            val requiredActions = resolveRequiredActions(service.actionValue)
            val matching = activityFilters.filter {
                it.pkg == service.pkg && it.actions.intersect(requiredActions).isNotEmpty()
            }

            if (matching.isEmpty()) {
                service.context.report(
                    ISSUE,
                    service.metadata,
                    service.context.getLocation(service.metadata),
                    "Wearable configuration action `${service.actionValue}` must be handled by an activity in the same package."
                )
                continue
            }

            if (service.minSdk < 30 && matching.none { it.hasWearableCategory }) {
                val filter = matching.first()
                filter.context.report(
                    ISSUE,
                    filter.intentFilter,
                    filter.context.getLocation(filter.intentFilter),
                    "Intent filter handling `${service.actionValue}` must include `<category android:name=\"$WEARABLE_CONFIGURATION_CATEGORY\" />` because `minSdkVersion` is less than 30."
                )
            }
        }
    }

    private fun resolveRequiredActions(value: String): Set<String> =
        if (value == WATCH_FACE_EDITOR_ACTION || value == WATCH_FACE_EDITOR_SHORT) {
            setOf(WATCH_FACE_EDITOR_ACTION, WATCH_FACE_EDITOR_SHORT)
        } else {
            setOf(value)
        }

    private fun Element.actions(): Set<String> {
        val result = mutableSetOf<String>()
        val actionNodes = getElementsByTagName(SdkConstants.TAG_ACTION)
        for (i in 0 until actionNodes.length) {
            val action = actionNodes.item(i) as? Element ?: continue
            val name = action.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (name.isNotEmpty()) result.add(name)
        }
        return result
    }

    private fun Element.hasCategory(categoryName: String): Boolean {
        val categoryNodes = getElementsByTagName(SdkConstants.TAG_CATEGORY)
        for (i in 0 until categoryNodes.length) {
            val category = categoryNodes.item(i) as? Element ?: continue
            val name = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (name == categoryName) return true
        }
        return false
    }

    private data class ServiceConfig(
        val context: XmlContext,
        val metadata: Element,
        val actionValue: String,
        val minSdk: Int,
        val pkg: String
    )

    private data class ActivityFilter(
        val intentFilter: Element,
        val actions: Set<String>,
        val hasWearableCategory: Boolean,
        val pkg: String,
        val context: XmlContext
    )

    companion object {
        private const val WATCH_FACE_EDITOR_ACTION =
            "com.google.android.wearable.watchface.WATCH_FACE_EDITOR"
        private const val WATCH_FACE_EDITOR_SHORT = "WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        private val WEARABLE_CONFIGURATION_METADATA_NAMES = setOf(
            "wearableConfigurationAction",
            "com.google.android.wearable.watchface.wearableConfigurationAction"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = """
                When a watch face service declares a <code>wearableConfigurationAction</code> metadata element, there must be an activity in the same package whose intent filter handles the same action. If <code>minSdkVersion</code> is less than 30, that intent filter must also include the <code>com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION</code> category.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}
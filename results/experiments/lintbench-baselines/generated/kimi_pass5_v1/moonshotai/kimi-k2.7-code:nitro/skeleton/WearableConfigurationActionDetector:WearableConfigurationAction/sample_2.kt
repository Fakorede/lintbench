package com.android.tools.lint.checks

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

private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
private const val WEARABLE_CONFIGURATION_ACTION = "wearableConfigurationAction"
private const val WATCH_FACE_EDITOR_VALUE = "WATCH_FACE_EDITOR"
private const val WATCH_FACE_EDITOR_ACTION = "com.google.android.wearable.watchface.WATCH_FACE_EDITOR"
private const val WEARABLE_CONFIGURATION_CATEGORY = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    private val services = mutableListOf<ServiceConfig>()
    private val activityFilters = mutableListOf<ActivityFilter>()

    override fun getApplicableElements(): Collection<String> =
        listOf("service", "activity", "activity-alias")

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.file.name != "AndroidManifest.xml") return

        val root = element.ownerDocument?.documentElement ?: return
        if (root.tagName != "manifest") return

        val packageName = root.getAttribute("package") ?: ""

        when (element.tagName) {
            "service" -> parseService(context, element, packageName)
            "activity", "activity-alias" -> parseActivity(element, packageName)
        }
    }

    private fun parseService(context: XmlContext, service: Element, packageName: String) {
        for (child in service.children()) {
            if (child.tagName != "meta-data") continue

            val name = child.androidAttr("name") ?: continue
            val value = child.androidAttr("value") ?: continue

            if (name == WEARABLE_CONFIGURATION_ACTION &&
                (value == WATCH_FACE_EDITOR_VALUE || value == WATCH_FACE_EDITOR_ACTION)
            ) {
                services.add(ServiceConfig(packageName, context.getLocation(child)))
            }
        }
    }

    private fun parseActivity(activity: Element, packageName: String) {
        for (child in activity.children()) {
            if (child.tagName != "intent-filter") continue

            val actions = mutableSetOf<String>()
            val categories = mutableSetOf<String>()

            for (node in child.children()) {
                when (node.tagName) {
                    "action" -> node.androidAttr("name")?.let { actions.add(it) }
                    "category" -> node.androidAttr("name")?.let { categories.add(it) }
                }
            }

            activityFilters.add(ActivityFilter(packageName, actions, categories))
        }
    }

    override fun checkMergedProject(context: Context) {
        val minSdk = context.mainProject.minSdk

        for (service in services) {
            val matched = activityFilters.any { filter ->
                filter.packageName == service.packageName &&
                    WATCH_FACE_EDITOR_ACTION in filter.actions &&
                    (minSdk >= 30 || WEARABLE_CONFIGURATION_CATEGORY in filter.categories)
            }

            if (!matched) {
                val categoryRequirement = if (minSdk < 30) {
                    " and include the category $WEARABLE_CONFIGURATION_CATEGORY"
                } else {
                    ""
                }

                context.report(
                    ISSUE,
                    service.location,
                    "The wearableConfigurationAction WATCH_FACE_EDITOR declared by this service must be " +
                        "handled by an activity in the same package with an intent filter for " +
                        "$WATCH_FACE_EDITOR_ACTION$categoryRequirement."
                )
            }
        }
    }

    private data class ServiceConfig(val packageName: String, val location: Location)
    private data class ActivityFilter(
        val packageName: String,
        val actions: Set<String>,
        val categories: Set<String>
    )

    companion object {
        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = "When a watch face service declares a `wearableConfigurationAction` metadata " +
                "value of `WATCH_FACE_EDITOR`, there must be a matching activity in the same package with " +
                "an intent filter for `com.google.android.wearable.watchface.WATCH_FACE_EDITOR`. If " +
                "`minSdkVersion` is below 30, the intent filter must also include the " +
                "`com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }
}

private fun Element.androidAttr(name: String): String? =
    getAttributeNS(ANDROID_URI, name).takeIf { it.isNotEmpty() }

private fun Element.children(): List<Element> =
    (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_METADATA
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

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    private data class ServiceInfo(
        val packageName: String,
        val location: Location
    )

    private data class ActivityInfo(
        val packageName: String,
        val location: Location,
        val context: XmlContext
    )

    private val servicesWithAction = mutableListOf<ServiceInfo>()
    private val candidateActivities = mutableListOf<ActivityInfo>()

    override fun getApplicableElements(): Collection<String> = listOf(TAG_SERVICE, TAG_ACTIVITY)

    override fun visitElement(context: XmlContext, element: Element) {
        val manifestPackage = context.document.documentElement.getAttribute("package")
        if (manifestPackage.isBlank()) return

        when (element.tagName) {
            TAG_SERVICE -> visitService(context, element, manifestPackage)
            TAG_ACTIVITY -> visitActivity(context, element, manifestPackage)
        }
    }

    private fun visitService(context: XmlContext, service: Element, manifestPackage: String) {
        val serviceName = service.attr(ATTR_NAME)
        if (serviceName.isBlank()) return

        val servicePackage = resolvePackage(serviceName, manifestPackage)

        for (child in service.childElements()) {
            if (child.tagName != TAG_METADATA) continue
            if (child.attr(ATTR_NAME) == META_DATA_NAME &&
                child.attr(ATTR_VALUE) == WATCH_FACE_EDITOR_ACTION
            ) {
                servicesWithAction.add(
                    ServiceInfo(servicePackage, context.getLocation(service))
                )
                break
            }
        }
    }

    private fun visitActivity(context: XmlContext, activity: Element, manifestPackage: String) {
        val activityName = activity.attr(ATTR_NAME)
        if (activityName.isBlank()) return

        val activityPackage = resolvePackage(activityName, manifestPackage)
        val minSdk = context.project.minSdk
        val requireCategory = minSdk != -1 && minSdk < 30

        for (intentFilter in activity.childElements()) {
            if (intentFilter.tagName != TAG_INTENT_FILTER) continue

            var hasAction = false
            var hasCategory = false
            for (child in intentFilter.childElements()) {
                when (child.tagName) {
                    TAG_ACTION -> if (child.attr(ATTR_NAME) == WATCH_FACE_EDITOR_ACTION) {
                        hasAction = true
                    }
                    TAG_CATEGORY -> if (child.attr(ATTR_NAME) == WEARABLE_CONFIGURATION_CATEGORY) {
                        hasCategory = true
                    }
                }
            }

            if (hasAction && (!requireCategory || hasCategory)) {
                candidateActivities.add(
                    ActivityInfo(activityPackage, context.getLocation(activity), context)
                )
                break
            }
        }
    }

    override fun beforeCheckRootProject(context: Context) {
        servicesWithAction.clear()
        candidateActivities.clear()
    }

    override fun afterCheckRootProject(context: Context) {
        val activitiesByPackage = candidateActivities.groupBy { it.packageName }
        val servicePackages = servicesWithAction.map { it.packageName }.toSet()

        for (servicePackage in servicePackages) {
            val matches = activitiesByPackage[servicePackage] ?: continue
            if (matches.size <= 1) continue

            val sorted = matches.sortedBy { it.location.start?.offset ?: 0 }
            var location = sorted.first().location
            for (i in 1 until sorted.size) {
                location = location.withSecondary(
                    sorted[i].location,
                    "Duplicate watch face configuration activity"
                )
            }

            sorted.first().context.report(
                ISSUE,
                location,
                "Duplicate watch face configuration activities found in package " +
                    "`$servicePackage`: ${matches.size} activities handle the " +
                    "`$WATCH_FACE_EDITOR_ACTION` action."
            )
        }
    }

    private fun Element.attr(name: String): String {
        val value = getAttributeNS(ANDROID_URI, name)
        return if (value.isNotEmpty()) value else getAttribute(name)
    }

    private fun Element.childElements(): List<Element> =
        (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

    private fun resolvePackage(name: String, manifestPackage: String): String = when {
        name.startsWith(".") -> manifestPackage
        name.contains(".") -> name.substringBeforeLast(".")
        else -> manifestPackage
    }

    companion object {
        private const val META_DATA_NAME = "wearableConfigurationAction"
        private const val WATCH_FACE_EDITOR_ACTION = "WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        @JvmField
        val ISSUE = Issue.create(
            "WearableActionDuplicate",
            "Duplicate watch face configuration activities found",
            """
                If a watch face service defines `wearableConfigurationAction` metadata with the value
                `WATCH_FACE_EDITOR`, there must be exactly one activity in the same package that has
                an intent filter for `WATCH_FACE_EDITOR` (along with
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` when
                `minSdkVersion` is less than 30).
            """.trimIndent(),
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}
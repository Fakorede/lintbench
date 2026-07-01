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
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = "When a watch face service defines wearableConfigurationAction metadata with the value WATCH_FACE_EDITOR, " +
                "there must be a corresponding activity in the same package with an intent filter for that action. " +
                "If minSdkVersion is less than 30, the intent filter must also include the " +
                "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION category.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? = emptyList()

    override fun visitElement(context: XmlContext, element: Element) {
        // Not used; validation is performed in checkMergedProject
    }

    override fun checkMergedProject(context: Context) {
        val manifest = context.project.manifest ?: return
        val document = try {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest)
        } catch (e: Exception) {
            return
        }

        val root = document.documentElement ?: return
        val appPackage = root.getAttribute("package").orEmpty()
        val minSdk = context.project.minSdkVersion?.apiLevel ?: 1

        val services = root.getElementsByTagName("service")
        val activities = root.getElementsByTagName("activity")

        // Map package -> list of activities that satisfy the intent filter requirements
        val validActivitiesByPackage = mutableMapOf<String, MutableList<String>>()

        for (i in 0 until activities.length) {
            val activity = activities.item(i) as Element
            val activityName = activity.getAttribute("android:name")
            val resolvedActivity = resolveClassName(activityName, appPackage)
            val activityPackage = resolvedActivity.substringBeforeLast('.', "")

            if (hasValidIntentFilter(activity, minSdk)) {
                validActivitiesByPackage.getOrPut(activityPackage) { mutableListOf() }.add(resolvedActivity)
            }
        }

        for (i in 0 until services.length) {
            val service = services.item(i) as Element
            if (!hasWearableConfigMetadata(service)) continue

            val serviceName = service.getAttribute("android:name")
            val resolvedService = resolveClassName(serviceName, appPackage)
            val servicePackage = resolvedService.substringBeforeLast('.', "")

            val matchingActivities = validActivitiesByPackage[servicePackage]
            if (matchingActivities.isNullOrEmpty()) {
                val categoryRequirement = if (minSdk < 30) {
                    " and the category `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION`"
                } else {
                    ""
                }
                context.report(
                    ISSUE,
                    Location.create(manifest),
                    "Watch face service `$resolvedService` defines `wearableConfigurationAction` metadata " +
                        "but no activity in package `$servicePackage` has an intent filter for " +
                        "`WATCH_FACE_EDITOR`$categoryRequirement."
                )
            }
        }
    }

    private fun resolveClassName(name: String, appPackage: String): String {
        return when {
            name.isEmpty() -> ""
            name.startsWith(".") -> "$appPackage$name"
            '.' in name -> name
            else -> "$appPackage.$name"
        }
    }

    private fun hasWearableConfigMetadata(service: Element): Boolean {
        val metaDatas = service.getElementsByTagName("meta-data")
        for (i in 0 until metaDatas.length) {
            val meta = metaDatas.item(i) as Element
            val metaName = meta.getAttribute("android:name")
            val metaValue = meta.getAttribute("android:value")
            if (metaName.endsWith("wearableConfigurationAction") && metaValue == "WATCH_FACE_EDITOR") {
                return true
            }
        }
        return false
    }

    private fun hasValidIntentFilter(activity: Element, minSdk: Int): Boolean {
        val filters = activity.getElementsByTagName("intent-filter")
        for (i in 0 until filters.length) {
            val filter = filters.item(i) as Element
            val actions = filter.getElementsByTagName("action")
            var hasAction = false

            for (j in 0 until actions.length) {
                val action = actions.item(j) as Element
                if (action.getAttribute("android:name").endsWith("WATCH_FACE_EDITOR")) {
                    hasAction = true
                    break
                }
            }

            if (!hasAction) continue

            // API 30+ does not require the wearable configuration category
            if (minSdk >= 30) return true

            val categories = filter.getElementsByTagName("category")
            for (j in 0 until categories.length) {
                val cat = categories.item(j) as Element
                if (cat.getAttribute("android:name") == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
                    return true
                }
            }
        }
        return false
    }
}
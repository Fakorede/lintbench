package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity

class WearableConfigurationActionDetector : Detector(), Detector.XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = "When a watch face service defines wearableConfigurationAction metadata with the value WATCH_FACE_EDITOR, there must be a corresponding activity in the same package with an intent filter for that action. If minSdkVersion is less than 30, the intent filter must also include the WEARABLE_CONFIGURATION category.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun checkMergedProject(context: Context) {
        val manifestFile = context.mainProject.mergedManifest ?: return
        val document = try {
            val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            factory.newDocumentBuilder().parse(manifestFile)
        } catch (e: Exception) {
            return
        }

        val root = document.documentElement ?: return
        val appPackage = root.getAttribute("package") ?: return
        val minSdk = context.mainProject.minSdkVersion

        val services = root.getElementsByTagName("service")
        val activities = root.getElementsByTagName("activity")

        val activityMap = mutableMapOf<String, org.w3c.dom.Element>()
        for (i in 0 until activities.length) {
            val activity = activities.item(i) as org.w3c.dom.Element
            val name = resolveClassName(appPackage, activity.getAttribute("android:name"))
            activityMap[name] = activity
        }

        for (i in 0 until services.length) {
            val service = services.item(i) as org.w3c.dom.Element
            val metadataList = service.getElementsByTagName("meta-data")
            for (j in 0 until metadataList.length) {
                val meta = metadataList.item(j) as org.w3c.dom.Element
                val nameAttr = meta.getAttribute("android:name")
                val valueAttr = meta.getAttribute("android:value")

                if (nameAttr.endsWith("wearableConfigurationAction") && valueAttr == "WATCH_FACE_EDITOR") {
                    val hasMatchingActivity = activityMap.keys.any { actName ->
                        actName.startsWith(appPackage) && hasRequiredIntentFilter(activityMap[actName]!!, minSdk)
                    }

                    if (!hasMatchingActivity) {
                        val location = context.getLocation(meta)
                        val categoryMsg = if (minSdk < 30) " and category com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION" else ""
                        context.report(
                            ISSUE,
                            location,
                            "No matching activity found for wearable configuration action. Ensure an activity in the same package has an intent filter with action WATCH_FACE_EDITOR$categoryMsg."
                        )
                    }
                }
            }
        }
    }

    private fun resolveClassName(pkg: String, className: String): String {
        return when {
            className.isEmpty() -> pkg
            className.startsWith(".") -> pkg + className
            className.contains(".") -> className
            else -> "$pkg.$className"
        }
    }

    private fun hasRequiredIntentFilter(activity: org.w3c.dom.Element, minSdk: Int): Boolean {
        val intentFilters = activity.getElementsByTagName("intent-filter")
        for (i in 0 until intentFilters.length) {
            val filter = intentFilters.item(i) as org.w3c.dom.Element
            if (!hasAction(filter, "WATCH_FACE_EDITOR")) continue

            if (minSdk < 30) {
                if (!hasCategory(filter, "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION")) continue
            }
            return true
        }
        return false
    }

    private fun hasAction(filter: org.w3c.dom.Element, actionName: String): Boolean {
        val actions = filter.getElementsByTagName("action")
        for (i in 0 until actions.length) {
            if ((actions.item(i) as org.w3c.dom.Element).getAttribute("android:name") == actionName) return true
        }
        return false
    }

    private fun hasCategory(filter: org.w3c.dom.Element, categoryName: String): Boolean {
        val categories = filter.getElementsByTagName("category")
        for (i in 0 until categories.length) {
            if ((categories.item(i) as org.w3c.dom.Element).getAttribute("android:name") == categoryName) return true
        }
        return false
    }
}
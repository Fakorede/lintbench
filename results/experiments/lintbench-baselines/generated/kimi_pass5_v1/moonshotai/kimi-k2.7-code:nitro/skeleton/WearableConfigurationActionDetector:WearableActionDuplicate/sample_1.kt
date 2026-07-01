package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private const val WEARABLE_CONFIGURATION_ACTION = "WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val META_DATA_NAME = "wearableConfigurationAction"

        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.MANIFEST_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                A watch face service that declares the `wearableConfigurationAction`
                metadata with the value `WATCH_FACE_EDITOR` expects exactly one
                activity in the same package to handle that action. Having more than
                one matching activity is redundant and can cause the system to show an
                unintended resolver when launching the watch face editor.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun checkMergedProject(context: Context) {
        val project = context.mainProject
        val document = project.mergedManifest ?: return
        val root = document.documentElement ?: return
        val packageName = root.getAttribute("package")
        val minSdk = project.minSdkVersion.featureLevel

        val application = root.getElementsByTagName("application").item(0) as? org.w3c.dom.Element
            ?: return

        val serviceActions = mutableSetOf<String>()
        val services = application.getElementsByTagName("service")
        for (i in 0 until services.length) {
            val service = services.item(i) as? org.w3c.dom.Element ?: continue
            val metadata = service.getElementsByTagName("meta-data")
            for (j in 0 until metadata.length) {
                val meta = metadata.item(j) as? org.w3c.dom.Element ?: continue
                if (meta.getAttributeNS(ANDROID_NS, "name") != META_DATA_NAME) continue
                val value = meta.getAttributeNS(ANDROID_NS, "value")
                if (value == WEARABLE_CONFIGURATION_ACTION) {
                    serviceActions.add(value)
                }
                break
            }
        }

        if (serviceActions.isEmpty()) return

        val manifestFile = try {
            document.documentURI?.let { java.io.File(java.net.URI(it)) }
        } catch (e: Exception) {
            null
        } ?: context.file

        val manifestText = if (manifestFile.isFile) {
            try {
                context.client.readFile(manifestFile).toString()
            } catch (e: Exception) {
                ""
            }
        } else {
            ""
        }

        val actionToActivities = mutableMapOf<String, MutableList<ConfigurationActivity>>()
        val activities = application.getElementsByTagName("activity")
        for (i in 0 until activities.length) {
            val activity = activities.item(i) as? org.w3c.dom.Element ?: continue
            val name = activity.getAttributeNS(ANDROID_NS, "name")
            if (name.isBlank()) continue

            val matchingActions = mutableSetOf<String>()
            val intentFilters = activity.getElementsByTagName("intent-filter")
            for (j in 0 until intentFilters.length) {
                val filter = intentFilters.item(j) as? org.w3c.dom.Element ?: continue
                val actions = filter.getActionNames()
                val categories = filter.getCategoryNames()
                for (action in actions) {
                    if (action !in serviceActions) continue
                    if (minSdk < 30 && WEARABLE_CONFIGURATION_CATEGORY !in categories) continue
                    matchingActions.add(action)
                }
            }

            if (matchingActions.isEmpty()) continue

            val location = findLocation(manifestFile, manifestText, name)
            val activityInfo = ConfigurationActivity(name, location)
            for (action in matchingActions) {
                actionToActivities.getOrPut(action) { mutableListOf() }.add(activityInfo)
            }
        }

        for ((action, activityList) in actionToActivities) {
            if (activityList.size <= 1) continue
            for (activity in activityList) {
                context.report(
                    ISSUE,
                    activity.location,
                    "Duplicate watch face configuration activity found for action `$action`: ${activity.name}"
                )
            }
        }
    }

    private data class ConfigurationActivity(
        val name: String,
        val location: Location,
    )

    private fun findLocation(file: java.io.File, text: String, name: String): Location {
        val pattern = "android:name=\"$name\""
        val index = text.indexOf(pattern)
        return if (index >= 0) {
            val nameStart = index + pattern.indexOf(name)
            val nameEnd = nameStart + name.length
            Location.create(file, text, nameStart, nameEnd)
        } else {
            Location.create(file)
        }
    }

    private fun org.w3c.dom.Element.getActionNames(): Set<String> {
        val names = mutableSetOf<String>()
        val actions = getElementsByTagName("action")
        for (i in 0 until actions.length) {
            val action = actions.item(i) as? org.w3c.dom.Element ?: continue
            val name = action.getAttributeNS(ANDROID_NS, "name")
            if (name.isNotBlank()) {
                names.add(name)
            }
        }
        return names
    }

    private fun org.w3c.dom.Element.getCategoryNames(): Set<String> {
        val names = mutableSetOf<String>()
        val categories = getElementsByTagName("category")
        for (i in 0 until categories.length) {
            val category = categories.item(i) as? org.w3c.dom.Element ?: continue
            val name = category.getAttributeNS(ANDROID_NS, "name")
            if (name.isNotBlank()) {
                names.add(name)
            }
        }
        return names
    }
}
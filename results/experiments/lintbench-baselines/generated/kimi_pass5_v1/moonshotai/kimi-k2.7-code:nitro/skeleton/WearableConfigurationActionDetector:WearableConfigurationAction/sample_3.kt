package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.SdkConstants.ANDROID_URI
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val WEARABLE_CONFIGURATION_ACTION =
            "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val WATCH_FACE_EDITOR = "WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = "When a watch face service declares a `wearableConfigurationAction` " +
                "metadata value of `WATCH_FACE_EDITOR`, there must be a corresponding activity " +
                "in the same package with an intent filter for that action. On devices running " +
                "API levels below 30, the activity's intent filter must also include the " +
                "`com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun checkMergedProject(context: Context) {
        val mergedManifest = context.mainProject.mergedManifest ?: return
        if (!mergedManifest.isFile) return

        val document = context.client.xmlParser.parseXml(mergedManifest) ?: return
        val root = document.documentElement ?: return
        val manifestPackage = root.getAttribute("package").takeIf { it.isNotBlank() } ?: return

        val services = root.getElementsByTagName("service")
        val activities = root.getElementsByTagName("activity")

        val requiredActions = mutableListOf<Pair<org.w3c.dom.Element, String>>()
        for (i in 0 until services.length) {
            val service = services.item(i) as? org.w3c.dom.Element ?: continue
            val metadata = findMetadata(service, WEARABLE_CONFIGURATION_ACTION) ?: continue
            val value = getAttributeValue(metadata, "value")
            if (value == WATCH_FACE_EDITOR) {
                requiredActions.add(metadata to value)
            }
        }

        if (requiredActions.isEmpty()) return

        val minSdk = context.mainProject.minSdk
        for ((metadata, action) in requiredActions) {
            if (!hasMatchingActivity(activities, action, manifestPackage, minSdk)) {
                val message = buildString {
                    append("Wearable configuration action `$action` must be handled by an activity ")
                    append("in the same package with an intent filter for `$action`")
                    if (minSdk < 30) {
                        append(" and the category `$WEARABLE_CONFIGURATION_CATEGORY`")
                    }
                    append(".")
                }
                context.report(
                    ISSUE,
                    Location.create(mergedManifest, metadata),
                    message,
                )
            }
        }
    }

    private fun findMetadata(
        parent: org.w3c.dom.Element,
        name: String
    ): org.w3c.dom.Element? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != org.w3c.dom.Node.ELEMENT_NODE) continue
            val element = node as org.w3c.dom.Element
            if (element.tagName == "meta-data" && getAttributeValue(element, "name") == name) {
                return element
            }
        }
        return null
    }

    private fun getAttributeValue(element: org.w3c.dom.Element, localName: String): String {
        val value = element.getAttributeNS(ANDROID_URI, localName)
        return value.ifEmpty { element.getAttribute("android:$localName") }
    }

    private fun hasMatchingActivity(
        activities: org.w3c.dom.NodeList,
        action: String,
        manifestPackage: String,
        minSdk: Int
    ): Boolean {
        for (i in 0 until activities.length) {
            val activity = activities.item(i) as? org.w3c.dom.Element ?: continue
            val name = getAttributeValue(activity, "name")
            if (name.isBlank()) continue
            if (getComponentPackage(name, manifestPackage) != manifestPackage) continue
            if (hasIntentFilter(activity, action, minSdk)) {
                return true
            }
        }
        return false
    }

    private fun getComponentPackage(name: String, manifestPackage: String): String {
        if (name.startsWith(".")) return manifestPackage
        val lastDot = name.lastIndexOf('.')
        return if (lastDot == -1) manifestPackage else name.substring(0, lastDot)
    }

    private fun hasIntentFilter(
        activity: org.w3c.dom.Element,
        action: String,
        minSdk: Int
    ): Boolean {
        val filters = activity.getElementsByTagName("intent-filter")
        for (i in 0 until filters.length) {
            val filter = filters.item(i) as? org.w3c.dom.Element ?: continue
            val actions = filter.getElementsByTagName("action")
            var actionFound = false
            for (j in 0 until actions.length) {
                val actionElement = actions.item(j) as? org.w3c.dom.Element ?: continue
                if (getAttributeValue(actionElement, "name") == action) {
                    actionFound = true
                    break
                }
            }
            if (!actionFound) continue

            if (minSdk >= 30) return true

            val categories = filter.getElementsByTagName("category")
            for (j in 0 until categories.length) {
                val categoryElement = categories.item(j) as? org.w3c.dom.Element ?: continue
                if (getAttributeValue(categoryElement, "name") == WEARABLE_CONFIGURATION_CATEGORY) {
                    return true
                }
            }
        }
        return false
    }
}
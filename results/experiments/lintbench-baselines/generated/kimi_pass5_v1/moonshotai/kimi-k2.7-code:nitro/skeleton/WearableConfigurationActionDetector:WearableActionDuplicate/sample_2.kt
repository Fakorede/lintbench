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

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                A watch face service that declares the `wearableConfigurationAction`
                metadata must have exactly one activity in the same package with an
                intent filter for the corresponding `WATCH_FACE_EDITOR` action (and
                the `WEARABLE_CONFIGURATION` category when `minSdkVersion` is less
                than 30). Having zero or more than one such activity is incorrect.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private const val ACTION_WATCH_FACE_SERVICE =
            "android.service.watchface.WatchFaceService"
        private const val META_WEARABLE_CONFIGURATION_ACTION =
            "wearableConfigurationAction"
        private const val ACTION_WATCH_FACE_EDITOR =
            "com.google.android.wearable.watchface.WATCH_FACE_EDITOR"
        private const val CATEGORY_WEARABLE_CONFIGURATION =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
    }

    override fun checkMergedProject(context: Context) {
        if (context.project != context.mainProject) {
            return
        }

        val mergedManifest = context.mainProject.mergedManifest ?: return
        val document = context.client.xmlParser.parseXml(mergedManifest, true) ?: return
        val root = document.documentElement ?: return
        val packageName = root.getAttribute("package")

        val watchFaceServices = root.getElementsByTagName("service").toElementList()
            .filter { it.isWatchFaceService() && it.hasConfigurationAction() }

        if (watchFaceServices.isEmpty()) {
            return
        }

        val requireWearableCategory = getMinSdkVersion(root) < 30
        val activities = root.getElementsByTagName("activity").toElementList()

        for (service in watchFaceServices) {
            val matches = mutableListOf<org.w3c.dom.Element>()
            for (activity in activities) {
                val name = activity.getAttributeNS(ANDROID_NS, "name")
                if (name.isEmpty() || !isInSamePackage(name, packageName)) {
                    continue
                }
                val filters = activity.getElementsByTagName("intent-filter").toElementList()
                if (filters.any { it.matchesEditorFilter(requireWearableCategory) }) {
                    matches.add(activity)
                }
            }

            when {
                matches.size > 1 -> {
                    report(context, service, "Duplicate watch face configuration activities found")
                }
                matches.isEmpty() -> {
                    report(
                        context,
                        service,
                        "Missing watch face configuration activity for the wearableConfigurationAction metadata"
                    )
                }
            }
        }
    }

    private fun report(context: Context, element: org.w3c.dom.Element, message: String) {
        val location = (context as? XmlContext)?.getLocation(element)
            ?: Location.create(context.file ?: context.mainProject.mergedManifest)
        context.report(ISSUE, location, message)
    }

    private fun org.w3c.dom.Element.isWatchFaceService(): Boolean {
        return getElementsByTagName("intent-filter")
            .toElementList()
            .any { filter ->
                filter.getElementsByTagName("action")
                    .toElementList()
                    .any { action ->
                        action.getAttributeNS(ANDROID_NS, "name") == ACTION_WATCH_FACE_SERVICE
                    }
            }
    }

    private fun org.w3c.dom.Element.hasConfigurationAction(): Boolean {
        return getElementsByTagName("meta-data")
            .toElementList()
            .any { meta ->
                meta.getAttributeNS(ANDROID_NS, "name") == META_WEARABLE_CONFIGURATION_ACTION &&
                        meta.getAttributeNS(ANDROID_NS, "value") == ACTION_WATCH_FACE_EDITOR
            }
    }

    private fun org.w3c.dom.Element.matchesEditorFilter(requireCategory: Boolean): Boolean {
        val hasAction = getElementsByTagName("action")
            .toElementList()
            .any { it.getAttributeNS(ANDROID_NS, "name") == ACTION_WATCH_FACE_EDITOR }
        if (!hasAction) {
            return false
        }
        if (requireCategory) {
            return getElementsByTagName("category")
                .toElementList()
                .any { it.getAttributeNS(ANDROID_NS, "name") == CATEGORY_WEARABLE_CONFIGURATION }
        }
        return true
    }

    private fun getMinSdkVersion(root: org.w3c.dom.Element): Int {
        val usesSdk = root.getElementsByTagName("uses-sdk").toElementList().firstOrNull()
        val value = usesSdk?.getAttributeNS(ANDROID_NS, "minSdkVersion") ?: ""
        return value.toIntOrNull() ?: 1
    }

    private fun isInSamePackage(name: String, packageName: String): Boolean {
        val fullName = when {
            name.startsWith(".") -> packageName + name
            name.contains(".") -> name
            else -> "$packageName.$name"
        }
        return fullName == packageName || fullName.startsWith("$packageName.")
    }

    private fun org.w3c.dom.NodeList.toElementList(): List<org.w3c.dom.Element> {
        val result = mutableListOf<org.w3c.dom.Element>()
        for (i in 0 until length) {
            val node = item(i)
            if (node is org.w3c.dom.Element) {
                result.add(node)
            }
        }
        return result
    }
}
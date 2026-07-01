package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.AndroidManifestConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val META_DATA_NAME = "wearableConfigurationAction"
        private const val ACTION_VALUE = "WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.MERGED_MANIFEST_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                A watch face service that declares the `wearableConfigurationAction`
                metadata with value `WATCH_FACE_EDITOR` must have exactly one
                corresponding activity in the same package. That activity must have an
                intent filter whose action is `WATCH_FACE_EDITOR`. When
                `minSdkVersion` is less than 30, the intent filter must also include the
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION`
                category.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String> = emptyList()

    override fun checkMergedProject(context: Context) {
        val mergedManifest = context.mainProject.mergedManifest ?: return
        if (!mergedManifest.isFile) return

        val document = context.client.xmlParser.parseXml(mergedManifest, context.mainProject)
            ?: return
        val root = document.documentElement ?: return
        val packageName = root.getAttribute(AndroidManifestConstants.ATTR_PACKAGE)
        if (packageName.isEmpty()) return

        val serviceConfigs = mutableListOf<Triple<String, org.w3c.dom.Element, org.w3c.dom.Element>>()
        val services = root.getElementsByTagName(AndroidManifestConstants.TAG_SERVICE)
        for (i in 0 until services.length) {
            val service = services.item(i) as? org.w3c.dom.Element ?: continue
            val meta = service.findMetaData() ?: continue
            val serviceName = service.getAttributeNS(
                AndroidManifestConstants.ANDROID_URI,
                AndroidManifestConstants.ATTR_NAME,
            )
            val servicePackage = computePackage(packageName, serviceName)
            serviceConfigs.add(Triple(servicePackage, service, meta))
        }

        val minSdk = context.mainProject.minSdk
        val matchingActivities = mutableListOf<Pair<String, org.w3c.dom.Element>>()
        val activityTags = listOf(
            AndroidManifestConstants.TAG_ACTIVITY,
            AndroidManifestConstants.TAG_ACTIVITY_ALIAS,
        )
        for (tag in activityTags) {
            val nodes = root.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val activity = nodes.item(i) as? org.w3c.dom.Element ?: continue
                if (!activity.hasWatchFaceConfigurationFilter(minSdk)) continue
                val activityName = activity.getAttributeNS(
                    AndroidManifestConstants.ANDROID_URI,
                    AndroidManifestConstants.ATTR_NAME,
                )
                val activityPackage = computePackage(packageName, activityName)
                matchingActivities.add(activityPackage to activity)
            }
        }

        for ((servicePackage, service, meta) in serviceConfigs) {
            val activitiesInPackage = matchingActivities.filter { it.first == servicePackage }
            when {
                activitiesInPackage.isEmpty() -> {
                    context.report(
                        ISSUE,
                        context.getLocation(meta),
                        "Missing watch face configuration activity for action $ACTION_VALUE",
                    )
                }
                activitiesInPackage.size > 1 -> {
                    for ((_, activity) in activitiesInPackage) {
                        context.report(
                            ISSUE,
                            context.getLocation(activity),
                            "Duplicate watch face configuration activities found",
                        )
                    }
                }
            }
        }
    }

    private fun org.w3c.dom.Element.findMetaData(): org.w3c.dom.Element? {
        val children = getElementsByTagName(AndroidManifestConstants.TAG_META_DATA)
        for (i in 0 until children.length) {
            val meta = children.item(i) as? org.w3c.dom.Element ?: continue
            val name = meta.getAttributeNS(
                AndroidManifestConstants.ANDROID_URI,
                AndroidManifestConstants.ATTR_NAME,
            )
            val value = meta.getAttributeNS(
                AndroidManifestConstants.ANDROID_URI,
                AndroidManifestConstants.ATTR_VALUE,
            )
            if (name == META_DATA_NAME && value == ACTION_VALUE) {
                return meta
            }
        }
        return null
    }

    private fun org.w3c.dom.Element.hasWatchFaceConfigurationFilter(minSdk: Int): Boolean {
        val filters = getElementsByTagName(AndroidManifestConstants.TAG_INTENT_FILTER)
        for (i in 0 until filters.length) {
            val filter = filters.item(i) as? org.w3c.dom.Element ?: continue
            var hasAction = false
            var hasCategory = false

            val actions = filter.getElementsByTagName(AndroidManifestConstants.TAG_ACTION)
            for (j in 0 until actions.length) {
                val action = actions.item(j) as? org.w3c.dom.Element ?: continue
                val actionName = action.getAttributeNS(
                    AndroidManifestConstants.ANDROID_URI,
                    AndroidManifestConstants.ATTR_NAME,
                )
                if (actionName == ACTION_VALUE) {
                    hasAction = true
                }
            }

            val categories = filter.getElementsByTagName(AndroidManifestConstants.TAG_CATEGORY)
            for (j in 0 until categories.length) {
                val category = categories.item(j) as? org.w3c.dom.Element ?: continue
                val categoryName = category.getAttributeNS(
                    AndroidManifestConstants.ANDROID_URI,
                    AndroidManifestConstants.ATTR_NAME,
                )
                if (categoryName == WEARABLE_CONFIGURATION_CATEGORY) {
                    hasCategory = true
                }
            }

            if (hasAction && (minSdk >= 30 || hasCategory)) {
                return true
            }
        }
        return false
    }

    private fun computePackage(rootPackage: String, name: String): String {
        if (name.isEmpty()) return rootPackage
        return when {
            name.startsWith(".") -> rootPackage
            name.contains(".") -> name.substring(0, name.lastIndexOf('.'))
            else -> rootPackage
        }
    }
}
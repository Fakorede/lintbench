package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    private val servicesByPkg = mutableMapOf<String, MutableList<Location>>()
    private val activitiesByPkg = mutableMapOf<String, MutableList<ActivityData>>()

    private data class ActivityData(val location: Location, val hasCategory: Boolean)

    companion object {
        private const val TAG_SERVICE = "service"
        private const val TAG_ACTIVITY = "activity"
        private const val TAG_META_DATA = "meta-data"
        private const val TAG_INTENT_FILTER = "intent-filter"
        private const val TAG_ACTION = "action"
        private const val TAG_CATEGORY = "category"

        private const val METADATA_NAME = "wearableConfigurationAction"
        private const val FULL_METADATA_NAME = "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val METADATA_VALUE = "WATCH_FACE_EDITOR"
        private const val ACTION_NAME = "WATCH_FACE_EDITOR"
        private const val CATEGORY_NAME = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = "When a watch face service defines wearableConfigurationAction metadata with the value WATCH_FACE_EDITOR, there must be a corresponding activity in the same package with an intent filter for WATCH_FACE_EDITOR. If minSdkVersion is less than 30, the intent filter must also include the category com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_SERVICE, TAG_ACTIVITY)

    override fun visitElement(context: XmlContext, element: Element) {
        val pkg = context.mainProject.packageName.orEmpty()
        val name = element.getAttribute(SdkConstants.ANDROID_URI, "name")
        if (name.isNullOrEmpty()) return

        if (element.tagName == TAG_SERVICE) {
            if (hasConfigMetadata(element)) {
                servicesByPkg.getOrPut(pkg) { mutableListOf() }.add(context.getLocation(element))
            }
        } else if (element.tagName == TAG_ACTIVITY) {
            val loc = context.getLocation(element)
            val data = analyzeActivity(element, loc)
            if (data != null) {
                activitiesByPkg.getOrPut(pkg) { mutableListOf() }.add(data)
            }
        }
    }

    private fun hasConfigMetadata(service: Element): Boolean {
        val children = service.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName == TAG_META_DATA) {
                val metaName = node.getAttribute(SdkConstants.ANDROID_URI, "name")
                val metaValue = node.getAttribute(SdkConstants.ANDROID_URI, "value")
                if ((metaName == METADATA_NAME || metaName == FULL_METADATA_NAME) && metaValue == METADATA_VALUE) {
                    return true
                }
            }
        }
        return false
    }

    private fun analyzeActivity(activity: Element, location: Location): ActivityData? {
        val children = activity.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName == TAG_INTENT_FILTER) {
                var hasAction = false
                var hasCategory = false
                val filterChildren = node.childNodes
                for (j in 0 until filterChildren.length) {
                    val filterNode = filterChildren.item(j)
                    if (filterNode is Element) {
                        val childName = filterNode.getAttribute(SdkConstants.ANDROID_URI, "name")
                        if (filterNode.tagName == TAG_ACTION && childName == ACTION_NAME) {
                            hasAction = true
                        }
                        if (filterNode.tagName == TAG_CATEGORY && childName == CATEGORY_NAME) {
                            hasCategory = true
                        }
                    }
                }
                if (hasAction) {
                    return ActivityData(location, hasCategory)
                }
            }
        }
        return null
    }

    override fun afterCheckEachProject(context: Context) {
        val minSdk = context.mainProject.minSdkVersion
        val requiresCategory = minSdk < 30

        for ((pkg, serviceLocs) in servicesByPkg) {
            val acts = activitiesByPkg[pkg]
            if (acts.isNullOrEmpty()) {
                for (loc in serviceLocs) {
                    context.report(
                        ISSUE, loc,
                        "Missing configuration activity: A watch face service with `wearableConfigurationAction` metadata requires an activity in the same package with an intent filter for `$ACTION_NAME`."
                    )
                }
            } else if (requiresCategory && acts.none { it.hasCategory }) {
                for (loc in serviceLocs) {
                    context.report(
                        ISSUE, loc,
                        "Missing configuration category: When `minSdkVersion` < 30, the configuration activity must include the category `$CATEGORY_NAME` in its intent filter."
                    )
                }
            }
        }
        servicesByPkg.clear()
        activitiesByPkg.clear()
    }
}
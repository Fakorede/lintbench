package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_META_DATA
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

    private val servicesByPackage = mutableSetOf<String>()
    private val activitiesByPackage = mutableMapOf<String, MutableList<Pair<Location, XmlContext>>>()

    override fun getApplicableElements(): Collection<String> = listOf(TAG_SERVICE, TAG_ACTIVITY)

    override fun visitElement(context: XmlContext, element: Element) {
        val manifestPackage = context.document.documentElement?.getAttribute("package") ?: return
        if (manifestPackage.isBlank()) return

        when (element.tagName) {
            TAG_SERVICE -> visitService(context, element, manifestPackage)
            TAG_ACTIVITY -> visitActivity(context, element, manifestPackage)
        }
    }

    private fun visitService(context: XmlContext, service: Element, manifestPackage: String) {
        for (child in service.childElements()) {
            if (child.tagName != TAG_META_DATA) continue
            val name = child.attr(ATTR_NAME)
            val value = child.attr(ATTR_VALUE)
            if (name == META_DATA_NAME && value.isWatchFaceEditorAction()) {
                servicesByPackage.add(manifestPackage)
                break
            }
        }
    }

    private fun visitActivity(context: XmlContext, activity: Element, manifestPackage: String) {
        val minSdk = context.project.minSdk
        val requireCategory = minSdk in 1..29

        for (intentFilter in activity.childElements()) {
            if (intentFilter.tagName != TAG_INTENT_FILTER) continue

            var hasAction = false
            var hasCategory = false
            for (child in intentFilter.childElements()) {
                when (child.tagName) {
                    TAG_ACTION -> {
                        if (child.attr(ATTR_NAME).isWatchFaceEditorAction()) {
                            hasAction = true
                        }
                    }
                    TAG_CATEGORY -> {
                        if (child.attr(ATTR_NAME) == WEARABLE_CONFIGURATION_CATEGORY) {
                            hasCategory = true
                        }
                    }
                }
            }

            if (hasAction && (!requireCategory || hasCategory)) {
                activitiesByPackage.getOrPut(manifestPackage) { mutableListOf() }
                    .add(context.getLocation(activity) to context)
                break
            }
        }
    }

    override fun beforeCheckRootProject(context: Context) {
        servicesByPackage.clear()
        activitiesByPackage.clear()
    }

    override fun afterCheckRootProject(context: Context) {
        for (packageName in servicesByPackage) {
            val activities = activitiesByPackage[packageName] ?: continue
            if (activities.size <= 1) continue

            val sorted = activities.sortedBy { it.first.start?.offset ?: 0 }
            var location = sorted.first().first
            for (i in 1 until sorted.size) {
                location = location.withSecondary(
                    sorted[i].first,
                    "Duplicate watch face configuration activity"
                )
            }

            sorted.first().second.report(
                ISSUE,
                location,
                "Duplicate watch face configuration activities found in package " +
                    "`$packageName`: ${activities.size} activities handle the " +
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

    private fun String.isWatchFaceEditorAction(): Boolean {
        return this == WATCH_FACE_EDITOR_ACTION ||
            this == "com.google.android.wearable.watchface.WATCH_FACE_EDITOR"
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
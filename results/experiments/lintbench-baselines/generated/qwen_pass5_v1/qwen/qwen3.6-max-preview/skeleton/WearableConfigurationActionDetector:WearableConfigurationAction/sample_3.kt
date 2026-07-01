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
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val METADATA_NAME_SUFFIX = "wearableConfigurationAction"
        private const val TARGET_VALUE = "WATCH_FACE_EDITOR"
        private const val REQUIRED_CATEGORY = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

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
        val manifest = context.mergedManifest ?: return
        val root = manifest.documentElement ?: return

        var hasTargetMetadata = false
        val services = root.getElementsByTagName("service")
        for (i in 0 until services.length) {
            val service = services.item(i) as? org.w3c.dom.Element ?: continue
            if (serviceHasMetadata(service)) {
                hasTargetMetadata = true
                break
            }
        }

        if (!hasTargetMetadata) return

        val minSdk = context.mainProject.minSdkVersion.coerceAtLeast(1)

        val activities = root.getElementsByTagName("activity")
        for (i in 0 until activities.length) {
            val activity = activities.item(i) as? org.w3c.dom.Element ?: continue
            if (activityHasMatchingIntentFilter(activity, minSdk)) {
                return
            }
        }

        val message = buildString {
            append("A service defines `wearableConfigurationAction` metadata with value `$TARGET_VALUE`, ")
            append("but no activity in the manifest has a matching intent filter. ")
            append("Add an `<activity>` with an `<intent-filter>` containing ")
            append("`<action android:name=\"$TARGET_VALUE\" />`")
            if (minSdk < 30) {
                append(" and `<category android:name=\"$REQUIRED_CATEGORY\" />`")
            }
            append(".")
        }

        val location = Location.create(context.mainProject.manifestFile ?: context.file)
        context.report(ISSUE, location, message)
    }

    private fun serviceHasMetadata(service: org.w3c.dom.Element): Boolean {
        val children = service.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is org.w3c.dom.Element && node.tagName == "meta-data") {
                val name = node.getAttributeNS(ANDROID_URI, "name")
                val value = node.getAttributeNS(ANDROID_URI, "value")
                if (name.endsWith(METADATA_NAME_SUFFIX) && value == TARGET_VALUE) {
                    return true
                }
            }
        }
        return false
    }

    private fun activityHasMatchingIntentFilter(activity: org.w3c.dom.Element, minSdk: Int): Boolean {
        val children = activity.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is org.w3c.dom.Element && node.tagName == "intent-filter") {
                if (intentFilterMatches(node, minSdk)) {
                    return true
                }
            }
        }
        return false
    }

    private fun intentFilterMatches(filter: org.w3c.dom.Element, minSdk: Int): Boolean {
        var hasAction = false
        var hasCategory = minSdk >= 30

        val children = filter.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is org.w3c.dom.Element) {
                val name = node.getAttributeNS(ANDROID_URI, "name")
                when (node.tagName) {
                    "action" -> {
                        if (name == TARGET_VALUE) hasAction = true
                    }
                    "category" -> {
                        if (name == REQUIRED_CATEGORY) hasCategory = true
                    }
                }
            }
        }
        return hasAction && hasCategory
    }
}
package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PACKAGE
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_ACTIVITY_ALIAS
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_SERVICE)

    override fun visitElement(context: XmlContext, element: Element) {
        if (!hasWearableConfigMetadata(element)) return

        val minSdk = try {
            context.project.minSdkVersion.apiLevel.coerceAtLeast(1)
        } catch (e: Exception) {
            1
        }

        val manifest = context.document.documentElement
        val manifestPackage = manifest.getAttribute(ATTR_PACKAGE)

        val servicePackage = getPackageFromClassName(element.getAttribute(ATTR_NAME), manifestPackage)

        val activities = manifest.getElementsByTagName(TAG_ACTIVITY)
        val aliases = manifest.getElementsByTagName(TAG_ACTIVITY_ALIAS)

        var hasMatchingActivity = false

        val checkActivity: (Element) -> Unit = { activity ->
            val activityPackage = getPackageFromClassName(activity.getAttribute(ATTR_NAME), manifestPackage)
            if (servicePackage == activityPackage && hasEditorIntentFilter(activity, minSdk)) {
                hasMatchingActivity = true
            }
        }

        for (i in 0 until activities.length) {
            checkActivity(activities.item(i) as Element)
            if (hasMatchingActivity) return
        }
        for (i in 0 until aliases.length) {
            checkActivity(aliases.item(i) as Element)
            if (hasMatchingActivity) return
        }

        if (!hasMatchingActivity) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "Watch face service defines `wearableConfigurationAction` metadata but no matching activity with `WATCH_FACE_EDITOR` intent filter exists in the same package."
            )
        }
    }

    private fun hasWearableConfigMetadata(service: Element): Boolean {
        val children = service.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == TAG_META_DATA) {
                val meta = node as Element
                val name = meta.getAttribute(ATTR_NAME)
                val value = meta.getAttribute(ATTR_VALUE)
                if (name.endsWith("wearableConfigurationAction") && value == "WATCH_FACE_EDITOR") {
                    return true
                }
            }
        }
        return false
    }

    private fun hasEditorIntentFilter(activity: Element, minSdk: Int): Boolean {
        val children = activity.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == TAG_INTENT_FILTER) {
                val filter = node as Element
                var hasAction = false
                var hasCategory = false
                val filterChildren = filter.childNodes
                for (j in 0 until filterChildren.length) {
                    val fc = filterChildren.item(j)
                    if (fc.nodeType == Node.ELEMENT_NODE) {
                        val fcElement = fc as Element
                        when (fcElement.tagName) {
                            TAG_ACTION -> {
                                if (fcElement.getAttribute(ATTR_NAME).endsWith("WATCH_FACE_EDITOR")) {
                                    hasAction = true
                                }
                            }
                            TAG_CATEGORY -> {
                                if (fcElement.getAttribute(ATTR_NAME).endsWith("WEARABLE_CONFIGURATION")) {
                                    hasCategory = true
                                }
                            }
                        }
                    }
                }
                if (hasAction && (minSdk >= 30 || hasCategory)) {
                    return true
                }
            }
        }
        return false
    }

    private fun getPackageFromClassName(className: String?, manifestPackage: String): String {
        if (className.isNullOrEmpty()) return manifestPackage
        val fqcn = when {
            className.startsWith(".") -> "$manifestPackage$className"
            "." !in className -> "$manifestPackage.$className"
            else -> className
        }
        val lastDot = fqcn.lastIndexOf('.')
        return if (lastDot > 0) fqcn.substring(0, lastDot) else manifestPackage
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = "Only when a watch face service defines `wearableConfigurationAction` metadata, with the value `WATCH_FACE_EDITOR`, there should be an activity in the same package, which has an intent filter for `WATCH_FACE_EDITOR` (with `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` if `minSdkVersion` is less than 30).",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}
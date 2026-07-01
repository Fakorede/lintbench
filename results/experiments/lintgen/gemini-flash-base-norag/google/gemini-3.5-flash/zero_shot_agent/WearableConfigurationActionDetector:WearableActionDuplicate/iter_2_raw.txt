package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), Detector.XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                If and only if a watch face service defines `wearableConfigurationAction` metadata, \
                with the value `WATCH_FACE_EDITOR`, there should be an activity in the same package, \
                which has an intent filter for `WATCH_FACE_EDITOR` (with \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` if minSdkVersion is less than 30).
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("manifest")
    }

    private fun getAndroidAttribute(element: Element, localName: String): String? {
        val attr = element.getAttributeNS(SdkConstants.ANDROID_URI, localName)
        if (attr.isNotEmpty()) return attr
        val attr2 = element.getAttribute("android:$localName")
        if (attr2.isNotEmpty()) return attr2
        return null
    }

    private fun Element.descendants(tagName: String): List<Element> {
        val result = mutableListOf<Element>()
        fun collect(element: Element) {
            var child = element.firstChild
            while (child != null) {
                if (child is Element) {
                    if (child.localName == tagName || child.tagName == tagName) {
                        result.add(child)
                    }
                    collect(child)
                }
                child = child.nextSibling
            }
        }
        collect(this)
        return result
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != "manifest" && element.localName != "manifest") return

        val services = element.descendants("service")
        val servicesWithMeta = mutableListOf<Element>()

        for (service in services) {
            val metaDatas = service.descendants("meta-data")
            for (meta in metaDatas) {
                val name = getAndroidAttribute(meta, "name")
                val value = getAndroidAttribute(meta, "value")
                if (name != null && value != null) {
                    val isConfigAction = name == "com.google.android.wearable.watchface.wearableConfigurationAction" ||
                            name == "wearableConfigurationAction" ||
                            name.endsWith(".wearableConfigurationAction")
                    val isWatchFaceEditor = value == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" ||
                            value == "WATCH_FACE_EDITOR" ||
                            value.endsWith(".WATCH_FACE_EDITOR")
                    if (isConfigAction && isWatchFaceEditor) {
                        servicesWithMeta.add(service)
                        break
                    }
                }
            }
        }

        val activities = element.descendants("activity")
        val matchingActivities = mutableListOf<Element>()
        val activitiesMissingCategory = mutableListOf<Element>()

        val minSdkVersion = context.project.minSdkVersion.apiLevel

        for (activity in activities) {
            val intentFilters = activity.descendants("intent-filter")
            var hasAction = false
            var hasCategory = false

            for (filter in intentFilters) {
                val actions = filter.descendants("action")
                for (action in actions) {
                    val actionName = getAndroidAttribute(action, "name")
                    if (actionName != null) {
                        if (actionName == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" ||
                            actionName == "WATCH_FACE_EDITOR" ||
                            actionName.endsWith(".WATCH_FACE_EDITOR")
                        ) {
                            hasAction = true
                        }
                    }
                }
                val categories = filter.descendants("category")
                for (category in categories) {
                    val catName = getAndroidAttribute(category, "name")
                    if (catName != null) {
                        if (catName == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION" ||
                            catName == "WEARABLE_CONFIGURATION" ||
                            catName.endsWith(".WEARABLE_CONFIGURATION")
                        ) {
                            hasCategory = true
                        }
                    }
                }
            }

            if (hasAction) {
                matchingActivities.add(activity)
                if (minSdkVersion < 30 && !hasCategory) {
                    activitiesMissingCategory.add(activity)
                }
            }
        }

        if (matchingActivities.size > 1) {
            for (activity in matchingActivities) {
                context.report(
                    ISSUE,
                    activity,
                    context.getNameLocation(activity),
                    "Duplicate watch face configuration activities found"
                )
            }
            return
        }

        if (servicesWithMeta.isNotEmpty() && matchingActivities.isEmpty()) {
            for (service in servicesWithMeta) {
                context.report(
                    ISSUE,
                    service,
                    context.getNameLocation(service),
                    "Watch face service defines WATCH_FACE_EDITOR configuration action, but no matching activity was found"
                )
            }
        }

        if (matchingActivities.isNotEmpty() && servicesWithMeta.isEmpty()) {
            for (activity in matchingActivities) {
                context.report(
                    ISSUE,
                    activity,
                    context.getNameLocation(activity),
                    "Activity defines WATCH_FACE_EDITOR intent filter, but no watch face service defines the corresponding wearableConfigurationAction metadata"
                )
            }
        }

        if (minSdkVersion < 30) {
            for (activity in activitiesMissingCategory) {
                context.report(
                    ISSUE,
                    activity,
                    context.getNameLocation(activity),
                    "Activity with WATCH_FACE_EDITOR action must also include WEARABLE_CONFIGURATION category when minSdkVersion < 30"
                )
            }
        }
    }
}
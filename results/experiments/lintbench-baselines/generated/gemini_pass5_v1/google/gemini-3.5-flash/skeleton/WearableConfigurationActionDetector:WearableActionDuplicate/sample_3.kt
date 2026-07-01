package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
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
                If a watch face service defines the `wearableConfigurationAction` metadata with the \
                value `WATCH_FACE_EDITOR`, there should be exactly one activity in the same package \
                which has an intent filter for `WATCH_FACE_EDITOR`. If minSdkVersion is less than 30, \
                this intent filter must also include the `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    private val servicesWithMetadata = mutableListOf<ServiceInfo>()
    private val activitiesWithAction = mutableListOf<ActivityInfo>()

    class ServiceInfo(
        val element: org.w3c.dom.Element,
        val context: XmlContext
    )

    class ActivityInfo(
        val element: org.w3c.dom.Element,
        val context: XmlContext,
        val hasCategory: Boolean
    )

    override fun getApplicableElements(): Collection<String> {
        return listOf("service", "activity")
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        if (context.file.name != "AndroidManifest.xml") return

        val tagName = element.tagName
        if (tagName == "service") {
            var hasMetadata = false
            var child = element.firstChild
            while (child != null) {
                if (child is org.w3c.dom.Element && child.tagName == "meta-data") {
                    val name = getAndroidAttribute(child, "name")
                    val value = getAndroidAttribute(child, "value")
                    if (name.endsWith("wearableConfigurationAction") && value.endsWith("WATCH_FACE_EDITOR")) {
                        hasMetadata = true
                    }
                }
                child = child.nextSibling
            }
            if (hasMetadata) {
                servicesWithMetadata.add(ServiceInfo(element, context))
            }
        } else if (tagName == "activity") {
            var hasAction = false
            var hasCategory = false
            var child = element.firstChild
            while (child != null) {
                if (child is org.w3c.dom.Element && child.tagName == "intent-filter") {
                    var filterChild = child.firstChild
                    var filterHasAction = false
                    var filterHasCategory = false
                    while (filterChild != null) {
                        if (filterChild is org.w3c.dom.Element) {
                            if (filterChild.tagName == "action") {
                                val name = getAndroidAttribute(filterChild, "name")
                                if (name.endsWith("WATCH_FACE_EDITOR")) {
                                    filterHasAction = true
                                }
                            } else if (filterChild.tagName == "category") {
                                val name = getAndroidAttribute(filterChild, "name")
                                if (name.endsWith("WEARABLE_CONFIGURATION")) {
                                    filterHasCategory = true
                                }
                            }
                        }
                        filterChild = filterChild.nextSibling
                    }
                    if (filterHasAction) {
                        hasAction = true
                        if (filterHasCategory) {
                            hasCategory = true
                        }
                    }
                }
                child = child.nextSibling
            }
            if (hasAction) {
                activitiesWithAction.add(ActivityInfo(element, context, hasCategory))
            }
        }
    }

    override fun checkMergedProject(context: Context) {
        val minSdkVersion = context.project.minSdkVersion.apiLevel

        val hasService = servicesWithMetadata.isNotEmpty()
        val hasActivity = activitiesWithAction.isNotEmpty()

        // 1. If and only if check
        if (hasService && !hasActivity) {
            for (service in servicesWithMetadata) {
                service.context.report(
                    ISSUE,
                    service.element,
                    service.context.getLocation(service.element),
                    "A watch face service defines `wearableConfigurationAction` but no matching activity with intent-filter `WATCH_FACE_EDITOR` was found."
                )
            }
        } else if (!hasService && hasActivity) {
            for (activity in activitiesWithAction) {
                activity.context.report(
                    ISSUE,
                    activity.element,
                    activity.context.getLocation(activity.element),
                    "An activity with intent-filter `WATCH_FACE_EDITOR` is defined, but no watch face service defines the `wearableConfigurationAction` metadata."
                )
            }
        }

        // 2. Duplicate check
        if (activitiesWithAction.size > 1) {
            for (activity in activitiesWithAction) {
                activity.context.report(
                    ISSUE,
                    activity.element,
                    activity.context.getLocation(activity.element),
                    "Duplicate watch face configuration activities found"
                )
            }
        }

        // 3. Category check (for minSdkVersion < 30)
        if (minSdkVersion < 30) {
            for (activity in activitiesWithAction) {
                if (!activity.hasCategory) {
                    activity.context.report(
                        ISSUE,
                        activity.element,
                        activity.context.getLocation(activity.element),
                        "Activities with `WATCH_FACE_EDITOR` action must also have the `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category if minSdkVersion is less than 30."
                    )
                }
            }
        }
    }

    private fun getAndroidAttribute(element: org.w3c.dom.Element, localName: String): String {
        return element.getAttributeNS("http://schemas.android.com/apk/res/android", localName).takeIf { it.isNotEmpty() }
            ?: element.getAttribute("android:$localName") ?: ""
    }
}
package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

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
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String> = listOf("manifest")

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != "manifest") return

        val services = element.getDescendantsByTagName("service")
        val activities = element.getDescendantsByTagName("activity")

        val servicesWithMeta = mutableListOf<Element>()
        val metaDataElements = mutableListOf<Element>()

        for (service in services) {
            val metaDatas = service.getDescendantsByTagName("meta-data")
            for (meta in metaDatas) {
                val name = meta.getAndroidAttribute("name")
                val value = meta.getAndroidAttribute("value")
                if ((name == "com.google.android.wearable.watchface.wearableConfigurationAction" || name == "wearableConfigurationAction") &&
                    (value == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" || value == "WATCH_FACE_EDITOR")
                ) {
                    servicesWithMeta.add(service)
                    metaDataElements.add(meta)
                }
            }
        }

        val activitiesWithAction = mutableSetOf<Element>()
        val intentFiltersWithAction = mutableListOf<Element>()
        val missingCategoryFilters = mutableListOf<Element>()

        val minSdkVersion = context.project.minSdkVersion.apiLevel

        for (activity in activities) {
            val intentFilters = activity.getDescendantsByTagName("intent-filter")
            for (filter in intentFilters) {
                val actions = filter.getDescendantsByTagName("action")
                var hasAction = false
                for (action in actions) {
                    val actionName = action.getAndroidAttribute("name")
                    if (actionName == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" || actionName == "WATCH_FACE_EDITOR") {
                        hasAction = true
                        break
                    }
                }
                if (hasAction) {
                    activitiesWithAction.add(activity)
                    intentFiltersWithAction.add(filter)

                    if (minSdkVersion < 30) {
                        val categories = filter.getDescendantsByTagName("category")
                        var hasCategory = false
                        for (cat in categories) {
                            val catName = cat.getAndroidAttribute("name")
                            if (catName == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION" || catName == "WEARABLE_CONFIGURATION") {
                                hasCategory = true
                                break
                            }
                        }
                        if (!hasCategory) {
                            missingCategoryFilters.add(filter)
                        }
                    }
                }
            }
        }

        // 1. Check for duplicates
        if (activitiesWithAction.size > 1) {
            for (activity in activitiesWithAction) {
                context.report(
                    ISSUE,
                    activity,
                    context.getNameLocation(activity),
                    "Duplicate watch face configuration activities found"
                )
            }
        }

        // 2. Check "if and only if"
        val hasServiceMeta = servicesWithMeta.isNotEmpty()
        val hasActivityAction = activitiesWithAction.isNotEmpty()

        if (hasServiceMeta && !hasActivityAction) {
            for (meta in metaDataElements) {
                context.report(
                    ISSUE,
                    meta,
                    context.getLocation(meta),
                    "A watch face service defines `wearableConfigurationAction` but no matching configuration activity was found"
                )
            }
        } else if (!hasServiceMeta && hasActivityAction) {
            for (filter in intentFiltersWithAction) {
                context.report(
                    ISSUE,
                    filter,
                    context.getLocation(filter),
                    "An activity is configured as a watch face editor, but no watch face service defines the corresponding `wearableConfigurationAction` metadata"
                )
            }
        }

        // 3. Check category for minSdkVersion < 30
        if (minSdkVersion < 30) {
            for (filter in missingCategoryFilters) {
                context.report(
                    ISSUE,
                    filter,
                    context.getLocation(filter),
                    "Watch face configuration activity intent-filter must include `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` when minSdkVersion < 30"
                )
            }
        }
    }

    private fun Element.getDescendantsByTagName(tagName: String): List<Element> {
        val results = mutableListOf<Element>()
        fun collect(element: Element) {
            val children = element.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child is Element) {
                    if (child.tagName == tagName) {
                        results.add(child)
                    }
                    collect(child)
                }
            }
        }
        collect(this)
        return results
    }

    private fun Element.getAndroidAttribute(localName: String): String {
        return this.getAttributeNS(SdkConstants.ANDROID_URI, localName).takeIf { it.isNotEmpty() }
            ?: this.getAttribute("android:$localName")
    }
}
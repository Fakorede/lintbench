package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PACKAGE
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_ACTIVITY_ALIAS
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_MANIFEST
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), Detector.XmlScanner {

    private data class ServiceConfig(
        val metadataElement: Element,
        val serviceName: String
    )

    private data class ActivityConfig(
        val activityName: String,
        val intentFilters: List<IntentFilterData>
    )

    private data class IntentFilterData(
        val actions: Set<String>,
        val categories: Set<String>
    )

    private val services = mutableListOf<ServiceConfig>()
    private val activities = mutableListOf<ActivityConfig>()
    private var manifestPackage: String? = null

    override fun getApplicableElements(): Collection<String>? = listOf(
        TAG_MANIFEST, TAG_SERVICE, TAG_ACTIVITY, TAG_ACTIVITY_ALIAS
    )

    override fun beforeCheckFile(context: Context) {
        services.clear()
        activities.clear()
        manifestPackage = null
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_MANIFEST -> {
                manifestPackage = element.getAttribute(ATTR_PACKAGE)
            }
            TAG_SERVICE -> {
                val metaDatas = element.getElementsByTagName(TAG_META_DATA)
                for (i in 0 until metaDatas.length) {
                    val meta = metaDatas.item(i) as Element
                    if (meta.getAttribute(ATTR_NAME) == "wearableConfigurationAction" &&
                        meta.getAttribute(ATTR_VALUE) == "WATCH_FACE_EDITOR") {
                        services.add(ServiceConfig(meta, element.getAttribute(ATTR_NAME)))
                    }
                }
            }
            TAG_ACTIVITY, TAG_ACTIVITY_ALIAS -> {
                val activityName = element.getAttribute(ATTR_NAME)
                val intentFilters = mutableListOf<IntentFilterData>()
                val filters = element.getElementsByTagName(TAG_INTENT_FILTER)
                for (i in 0 until filters.length) {
                    val filter = filters.item(i) as Element
                    val actions = mutableSetOf<String>()
                    val categories = mutableSetOf<String>()
                    val children = filter.childNodes
                    for (j in 0 until children.length) {
                        val child = children.item(j)
                        if (child is Element) {
                            when (child.tagName) {
                                TAG_ACTION -> actions.add(child.getAttribute(ATTR_NAME))
                                TAG_CATEGORY -> categories.add(child.getAttribute(ATTR_NAME))
                            }
                        }
                    }
                    intentFilters.add(IntentFilterData(actions, categories))
                }
                activities.add(ActivityConfig(activityName, intentFilters))
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        if (context !is XmlContext) return
        val pkg = manifestPackage ?: return
        val minSdk = context.project.minSdkVersion.takeIf { it > 0 } ?: 1

        for (service in services) {
            val servicePkg = getPackageName(service.serviceName, pkg)
            var foundMatchingActivity = false

            for (activity in activities) {
                val activityPkg = getPackageName(activity.activityName, pkg)
                if (servicePkg != activityPkg) continue

                for (filter in activity.intentFilters) {
                    if ("WATCH_FACE_EDITOR" in filter.actions) {
                        if (minSdk < 30) {
                            if ("com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION" in filter.categories) {
                                foundMatchingActivity = true
                                break
                            }
                        } else {
                            foundMatchingActivity = true
                            break
                        }
                    }
                }
                if (foundMatchingActivity) break
            }

            if (!foundMatchingActivity) {
                val message = if (minSdk < 30) {
                    "Wearable configuration action metadata requires an activity in the same package with an intent filter for action `WATCH_FACE_EDITOR` and category `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION`"
                } else {
                    "Wearable configuration action metadata requires an activity in the same package with an intent filter for action `WATCH_FACE_EDITOR`"
                }
                context.report(
                    ISSUE,
                    context.getLocation(service.metadataElement),
                    message
                )
            }
        }
    }

    private fun getPackageName(className: String, defaultPackage: String): String {
        val fqn = when {
            className.isEmpty() -> defaultPackage
            className.startsWith(".") -> defaultPackage + className
            !className.contains(".") -> "$defaultPackage.$className"
            else -> className
        }
        return fqn.substringBeforeLast(".", "")
    }

    companion object {
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = "When a watch face service defines `wearableConfigurationAction` metadata with the value `WATCH_FACE_EDITOR`, there must be an activity in the same package that declares an intent filter for the `WATCH_FACE_EDITOR` action. If `minSdkVersion` is less than 30, the intent filter must also include the `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category.",
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
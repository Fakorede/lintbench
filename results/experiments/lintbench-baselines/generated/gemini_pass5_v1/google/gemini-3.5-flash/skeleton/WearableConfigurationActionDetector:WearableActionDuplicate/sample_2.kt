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

    private val services = mutableListOf<ServiceInfo>()
    private val activities = mutableListOf<ActivityInfo>()

    class ServiceInfo(
        val className: String,
        val hasMetadata: Boolean,
        val location: Location
    )

    class ActivityInfo(
        val className: String,
        val hasAction: Boolean,
        val hasCategory: Boolean,
        val location: Location
    )

    companion object {
        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = "If and only if a watch face service defines wearableConfigurationAction metadata, with the value WATCH_FACE_EDITOR, there should be an activity in the same package, which has an intent filter for WATCH_FACE_EDITOR (with com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION if minSdkVersion is less than 30).",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun beforeCheckProject(context: Context) {
        services.clear()
        activities.clear()
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("service", "activity")
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val tagName = element.tagName
        val manifestElement = element.ownerDocument.documentElement
        val packageName = manifestElement.getAttribute("package") ?: ""
        val rawName = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name") ?: ""
        val fqName = getFullyQualifiedName(rawName, packageName)

        if (tagName == "service") {
            var hasMetadata = false
            val childNodes = element.childNodes
            for (i in 0 until childNodes.length) {
                val child = childNodes.item(i)
                if (child is org.w3c.dom.Element && child.tagName == "meta-data") {
                    val metaName = child.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
                    val metaValue = child.getAttributeNS("http://schemas.android.com/apk/res/android", "value")
                    if ((metaName == "com.google.android.wearable.watchface.wearableConfigurationAction" || metaName == "wearableConfigurationAction") &&
                        (metaValue == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" || metaValue == "WATCH_FACE_EDITOR")) {
                        hasMetadata = true
                    }
                }
            }
            services.add(ServiceInfo(fqName, hasMetadata, context.getLocation(element)))
        } else if (tagName == "activity") {
            var hasAction = false
            var hasCategory = false
            val childNodes = element.childNodes
            for (i in 0 until childNodes.length) {
                val child = childNodes.item(i)
                if (child is org.w3c.dom.Element && child.tagName == "intent-filter") {
                    val filterChildren = child.childNodes
                    for (j in 0 until filterChildren.length) {
                        val filterChild = filterChildren.item(j)
                        if (filterChild is org.w3c.dom.Element) {
                            if (filterChild.tagName == "action") {
                                val actionName = filterChild.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
                                if (actionName == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" || actionName == "WATCH_FACE_EDITOR") {
                                    hasAction = true
                                }
                            } else if (filterChild.tagName == "category") {
                                val categoryName = filterChild.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
                                if (categoryName == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION" || categoryName == "WEARABLE_CONFIGURATION") {
                                    hasCategory = true
                                }
                            }
                        }
                    }
                }
            }
            activities.add(ActivityInfo(fqName, hasAction, hasCategory, context.getLocation(element)))
        }
    }

    override fun checkMergedProject(context: Context) {
        val minSdkVersion = context.project.minSdkVersion.apiLevel

        val allPackages = (services.map { getJavaPackage(it.className) } +
                           activities.map { getJavaPackage(it.className) }).toSet()

        for (pkg in allPackages) {
            val pkgServices = services.filter { getJavaPackage(it.className) == pkg && it.hasMetadata }
            val pkgActivities = activities.filter { getJavaPackage(it.className) == pkg }

            val validActivities = pkgActivities.filter { matchesCriteria(it, minSdkVersion) }

            if (validActivities.size > 1) {
                for (activity in validActivities) {
                    context.report(
                        ISSUE,
                        activity.location,
                        "Duplicate watch face configuration activities found in package $pkg"
                    )
                }
            }

            if (pkgServices.isNotEmpty()) {
                if (validActivities.isEmpty()) {
                    val almostMatching = pkgActivities.filter { it.hasAction && !it.hasCategory && minSdkVersion < 30 }
                    if (almostMatching.isNotEmpty()) {
                        for (activity in almostMatching) {
                            context.report(
                                ISSUE,
                                activity.location,
                                "Activity is missing the `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category, which is required when minSdkVersion is less than 30"
                            )
                        }
                    } else {
                        for (service in pkgServices) {
                            context.report(
                                ISSUE,
                                service.location,
                                "Watch face service defines `wearableConfigurationAction` but no matching activity with intent-filter `WATCH_FACE_EDITOR` was found in package $pkg"
                            )
                        }
                    }
                }
            } else {
                if (validActivities.isNotEmpty()) {
                    for (activity in validActivities) {
                        context.report(
                            ISSUE,
                            activity.location,
                            "Watch face configuration activity found in package $pkg but no watch face service defines the `wearableConfigurationAction` metadata"
                        )
                    }
                }
            }
        }
    }

    private fun matchesCriteria(activity: ActivityInfo, minSdkVersion: Int): Boolean {
        if (!activity.hasAction) return false
        if (minSdkVersion < 30 && !activity.hasCategory) return false
        return true
    }

    private fun getFullyQualifiedName(name: String, packageName: String): String {
        if (name.startsWith(".")) {
            return packageName + name
        }
        if (!name.contains(".")) {
            return if (packageName.isEmpty()) name else "$packageName.$name"
        }
        return name
    }

    private fun getJavaPackage(className: String): String {
        val lastDot = className.lastIndexOf('.')
        return if (lastDot != -1) className.substring(0, lastDot) else ""
    }
}
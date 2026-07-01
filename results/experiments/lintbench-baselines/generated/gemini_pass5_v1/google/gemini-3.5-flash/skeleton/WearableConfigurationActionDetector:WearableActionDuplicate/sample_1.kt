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

    private class ServiceInfo(
        val name: String,
        val packageName: String,
        val location: Location
    )

    private class ActivityInfo(
        val name: String,
        val packageName: String,
        val location: Location,
        val hasCategory: Boolean
    )

    companion object {
        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.MANIFEST_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                If a watch face service defines the `wearableConfigurationAction` metadata with the value \
                `WATCH_FACE_EDITOR`, there must be exactly one corresponding activity in the same package \
                with an intent filter for `WATCH_FACE_EDITOR`.
                
                Additionally, if the `minSdkVersion` is less than 30, this intent filter must also include \
                the category `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun beforeCheckEachProject(context: Context) {
        services.clear()
        activities.clear()
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("service", "activity")
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val tagName = element.tagName
        val root = element.ownerDocument?.documentElement
        val manifestPackage = root?.getAttribute("package") ?: ""

        if (tagName == "service") {
            var hasMetadata = false
            val childNodes = element.childNodes
            for (i in 0 until childNodes.length) {
                val child = childNodes.item(i)
                if (child is org.w3c.dom.Element && child.tagName == "meta-data") {
                    val name = getAndroidAttribute(child, "name")
                    if (name == "com.google.android.wearable.watchface.wearableConfigurationAction") {
                        val value = getAndroidAttribute(child, "value")
                        if (value.endsWith("WATCH_FACE_EDITOR")) {
                            hasMetadata = true
                        }
                    }
                }
            }
            if (hasMetadata) {
                val rawName = getAndroidAttribute(element, "name")
                val fullName = getAbsoluteClassName(rawName, manifestPackage)
                val pkgName = getPackageNameOfClass(fullName)
                services.add(
                    ServiceInfo(
                        name = fullName,
                        packageName = pkgName,
                        location = context.getLocation(element)
                    )
                )
            }
        } else if (tagName == "activity") {
            val childNodes = element.childNodes
            for (i in 0 until childNodes.length) {
                val child = childNodes.item(i)
                if (child is org.w3c.dom.Element && child.tagName == "intent-filter") {
                    var hasEditorAction = false
                    var hasCategory = false

                    val filterChildren = child.childNodes
                    for (j in 0 until filterChildren.length) {
                        val filterChild = filterChildren.item(j)
                        if (filterChild is org.w3c.dom.Element) {
                            if (filterChild.tagName == "action") {
                                val actionName = getAndroidAttribute(filterChild, "name")
                                if (actionName.endsWith("WATCH_FACE_EDITOR")) {
                                    hasEditorAction = true
                                }
                            } else if (filterChild.tagName == "category") {
                                val catName = getAndroidAttribute(filterChild, "name")
                                if (catName == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
                                    hasCategory = true
                                }
                            }
                        }
                    }

                    if (hasEditorAction) {
                        val rawName = getAndroidAttribute(element, "name")
                        val fullName = getAbsoluteClassName(rawName, manifestPackage)
                        val pkgName = getPackageNameOfClass(fullName)
                        activities.add(
                            ActivityInfo(
                                name = fullName,
                                packageName = pkgName,
                                location = context.getLocation(element),
                                hasCategory = hasCategory
                            )
                        )
                    }
                }
            }
        }
    }

    override fun checkMergedProject(context: Context) {
        val minSdk = context.project.minSdkVersion.apiLevel

        val servicesByPackage = services.groupBy { it.packageName }
        val activitiesByPackage = activities.groupBy { it.packageName }
        val allPackages = (servicesByPackage.keys + activitiesByPackage.keys).distinct()

        for (pkg in allPackages) {
            val pkgServices = servicesByPackage[pkg] ?: emptyList()
            val pkgActivities = activitiesByPackage[pkg] ?: emptyList()

            val validActivities = pkgActivities.filter { activity ->
                if (minSdk < 30) {
                    activity.hasCategory
                } else {
                    true
                }
            }

            val hasService = pkgServices.isNotEmpty()

            if (hasService) {
                if (validActivities.isEmpty()) {
                    for (service in pkgServices) {
                        context.report(
                            ISSUE,
                            service.location,
                            "Watch face service defines wearableConfigurationAction but no matching configuration activity was found in package $pkg"
                        )
                    }
                } else if (validActivities.size > 1) {
                    for (activity in validActivities) {
                        context.report(
                            ISSUE,
                            activity.location,
                            "Duplicate watch face configuration activity found in package $pkg"
                        )
                    }
                }
            } else {
                if (validActivities.isNotEmpty()) {
                    for (activity in validActivities) {
                        context.report(
                            ISSUE,
                            activity.location,
                            "Watch face configuration activity found in package $pkg but no watch face service defines the corresponding wearableConfigurationAction metadata"
                        )
                    }
                }
            }
        }
    }

    private fun getAndroidAttribute(element: org.w3c.dom.Element, localName: String): String {
        val val1 = element.getAttributeNS("http://schemas.android.com/apk/res/android", localName)
        if (val1.isNotEmpty()) {
            return val1
        }
        return element.getAttribute("android:$localName") ?: ""
    }

    private fun getAbsoluteClassName(className: String, packageName: String): String {
        if (className.startsWith(".")) {
            return packageName + className
        }
        if (!className.contains(".")) {
            return if (packageName.isEmpty()) className else "$packageName.$className"
        }
        return className
    }

    private fun getPackageNameOfClass(className: String): String {
        val lastDot = className.lastIndexOf('.')
        return if (lastDot != -1) className.substring(0, lastDot) else ""
    }
}
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
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
                A watch face service can define a configuration activity using the metadata \
                `wearableConfigurationAction`. There should be at most one configuration activity \
                for each watch face service, and it must be located in the same package and \
                have the correct intent-filter configuration.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun checkMergedProject(context: Context) {
        val document = context.client.getMergedManifest(context.project) ?: return
        val root = document.documentElement ?: return

        val manifestPackage = root.getAttribute("package") ?: ""

        val servicesList = root.getElementsByTagName("service")
        val activitiesList = root.getElementsByTagName("activity")

        class ServiceInfo(
            val element: org.w3c.dom.Element,
            val fqName: String,
            val packageName: String,
            val configActions: List<String>,
            val metadataElements: List<org.w3c.dom.Element>
        )

        class IntentFilterInfo(
            val actions: List<String>,
            val categories: List<String>,
            val element: org.w3c.dom.Element
        )

        class ActivityInfo(
            val element: org.w3c.dom.Element,
            val fqName: String,
            val packageName: String,
            val intentFilters: List<IntentFilterInfo>
        )

        fun getAndroidAttribute(element: org.w3c.dom.Element, localName: String): String {
            val val1 = element.getAttributeNS("http://schemas.android.com/apk/res/android", localName)
            if (!val1.isNullOrEmpty()) return val1
            return element.getAttribute("android:$localName") ?: ""
        }

        fun resolveClassName(name: String, manifestPkg: String): String {
            return when {
                name.isEmpty() -> ""
                name.startsWith(".") -> manifestPkg + name
                !name.contains(".") -> "$manifestPkg.$name"
                else -> name
            }
        }

        fun getPackageNameOfClass(fqName: String): String {
            val lastDot = fqName.lastIndexOf('.')
            return if (lastDot != -1) fqName.substring(0, lastDot) else ""
        }

        val services = mutableListOf<ServiceInfo>()

        for (i in 0 until servicesList.length) {
            val serviceElement = servicesList.item(i) as org.w3c.dom.Element
            val serviceName = getAndroidAttribute(serviceElement, "name")
            val serviceFqName = resolveClassName(serviceName, manifestPackage)
            val servicePackage = getPackageNameOfClass(serviceFqName)

            val configActions = mutableListOf<String>()
            val metadataElements = mutableListOf<org.w3c.dom.Element>()

            val childNodes = serviceElement.childNodes
            for (j in 0 until childNodes.length) {
                val child = childNodes.item(j)
                if (child is org.w3c.dom.Element && child.tagName == "meta-data") {
                    val name = getAndroidAttribute(child, "name")
                    if (name == "com.google.android.wearable.watchface.wearableConfigurationAction" ||
                        name == "android.support.wearable.watchface.wearableConfigurationAction"
                    ) {
                        val value = getAndroidAttribute(child, "value")
                        if (value.isNotEmpty()) {
                            configActions.add(value)
                            metadataElements.add(child)
                        }
                    }
                }
            }

            if (configActions.isNotEmpty()) {
                services.add(ServiceInfo(serviceElement, serviceFqName, servicePackage, configActions, metadataElements))
            }
        }

        val activities = mutableListOf<ActivityInfo>()

        for (i in 0 until activitiesList.length) {
            val activityElement = activitiesList.item(i) as org.w3c.dom.Element
            val activityName = getAndroidAttribute(activityElement, "name")
            val activityFqName = resolveClassName(activityName, manifestPackage)
            val activityPackage = getPackageNameOfClass(activityFqName)

            val intentFilters = mutableListOf<IntentFilterInfo>()
            val childNodes = activityElement.childNodes
            for (j in 0 until childNodes.length) {
                val child = childNodes.item(j)
                if (child is org.w3c.dom.Element && child.tagName == "intent-filter") {
                    val filterActions = mutableListOf<String>()
                    val filterCategories = mutableListOf<String>()
                    val filterChildren = child.childNodes
                    for (k in 0 until filterChildren.length) {
                        val filterChild = filterChildren.item(k)
                        if (filterChild is org.w3c.dom.Element) {
                            if (filterChild.tagName == "action") {
                                val actionName = getAndroidAttribute(filterChild, "name")
                                if (actionName.isNotEmpty()) {
                                    filterActions.add(actionName)
                                }
                            } else if (filterChild.tagName == "category") {
                                val catName = getAndroidAttribute(filterChild, "name")
                                if (catName.isNotEmpty()) {
                                    filterCategories.add(catName)
                                }
                            }
                        }
                    }
                    intentFilters.add(IntentFilterInfo(filterActions, filterCategories, child))
                }
            }
            activities.add(ActivityInfo(activityElement, activityFqName, activityPackage, intentFilters))
        }

        val allServiceActions = services.flatMap { it.configActions }.toSet()
        val minSdk = context.project.minSdkVersion.apiLevel
        val checkCategory = minSdk < 30

        for (service in services) {
            for (idx in service.configActions.indices) {
                val action = service.configActions[idx]
                val metadataElement = service.metadataElements[idx]

                val allMatches = mutableListOf<ActivityInfo>()
                val samePackageMatches = mutableListOf<ActivityInfo>()

                for (activity in activities) {
                    var matchesAction = false
                    var matchesCategory = !checkCategory

                    for (filter in activity.intentFilters) {
                        if (filter.actions.contains(action)) {
                            matchesAction = true
                            if (checkCategory && filter.categories.contains("com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION")) {
                                matchesCategory = true
                            }
                        }
                    }

                    if (matchesAction && matchesCategory) {
                        allMatches.add(activity)
                        if (activity.packageName == service.packageName) {
                            samePackageMatches.add(activity)
                        }
                    }
                }

                if (samePackageMatches.isEmpty()) {
                    if (allMatches.isNotEmpty()) {
                        val location = context.client.getXmlParser().getLocation(metadataElement)
                        context.report(
                            ISSUE,
                            metadataElement,
                            location,
                            "Watch face configuration activity for action `$action` must be in the same package as the service (`${service.packageName}`)"
                        )
                    } else {
                        val location = context.client.getXmlParser().getLocation(metadataElement)
                        val categorySuffix = if (checkCategory) " and category `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION`" else ""
                        context.report(
                            ISSUE,
                            metadataElement,
                            location,
                            "Missing watch face configuration activity with action `$action`$categorySuffix"
                        )
                    }
                } else if (samePackageMatches.size > 1) {
                    for (activity in samePackageMatches) {
                        val location = context.client.getXmlParser().getLocation(activity.element)
                        context.report(
                            ISSUE,
                            activity.element,
                            location,
                            "Duplicate watch face configuration activities found for action `$action`"
                        )
                    }
                }
            }
        }

        for (activity in activities) {
            for (filter in activity.intentFilters) {
                val hasConfigCategory = filter.categories.contains("com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION")
                if (hasConfigCategory) {
                    val hasMatchingService = filter.actions.any { allServiceActions.contains(it) }
                    if (!hasMatchingService) {
                        val location = context.client.getXmlParser().getLocation(filter.element)
                        context.report(
                            ISSUE,
                            filter.element,
                            location,
                            "Watch face configuration activity found but no watch face service defines a matching configuration action in its metadata"
                        )
                    }
                }
            }
        }
    }
}
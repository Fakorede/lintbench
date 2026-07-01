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
            Scope.MANIFEST_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = """
                When a watch face service defines the `wearableConfigurationAction` metadata element with the value `WATCH_FACE_EDITOR`, there must be an activity in the same package that has an intent filter for `WATCH_FACE_EDITOR`.
                
                If the `minSdkVersion` is less than 30, this intent filter must also include the category `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun checkMergedProject(context: Context) {
        val manifestFiles = context.project.manifestFiles
        for (manifestFile in manifestFiles) {
            val document = context.client.getXmlParser().parseXml(manifestFile) ?: continue
            val root = document.documentElement ?: continue
            checkManifest(context, manifestFile, root)
        }
    }

    private fun checkManifest(context: Context, file: java.io.File, manifest: org.w3c.dom.Element) {
        val application = manifest.getElementsByTagName("application").item(0) as? org.w3c.dom.Element ?: return
        
        val services = application.getElementsByTagName("service")
        val activities = application.getElementsByTagName("activity")
        
        for (i in 0 until services.length) {
            val service = services.item(i) as? org.w3c.dom.Element ?: continue
            val metaDatas = service.getElementsByTagName("meta-data")
            var hasWatchFaceEditor = false
            var configMetadata: org.w3c.dom.Element? = null
            
            for (j in 0 until metaDatas.length) {
                val metaData = metaDatas.item(j) as? org.w3c.dom.Element ?: continue
                val name = metaData.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
                if (name == "com.google.android.wearable.watchface.wearableConfigurationAction") {
                    val value = metaData.getAttributeNS("http://schemas.android.com/apk/res/android", "value")
                    if (value == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" ||
                        value == "WATCH_FACE_EDITOR") {
                        hasWatchFaceEditor = true
                        configMetadata = metaData
                        break
                    }
                }
            }
            
            if (hasWatchFaceEditor && configMetadata != null) {
                val packageName = manifest.getAttribute("package") ?: ""
                val minSdkVersion = context.project.minSdkVersion.featureLevel
                
                var matchingActivityFound = false
                
                for (k in 0 until activities.length) {
                    val activity = activities.item(k) as? org.w3c.dom.Element ?: continue
                    val activityName = activity.getAttributeNS("http://schemas.android.com/apk/res/android", "name") ?: ""
                    val fullActivityName = if (activityName.startsWith(".")) {
                        packageName + activityName
                    } else if (!activityName.contains(".")) {
                        "$packageName.$activityName"
                    } else {
                        activityName
                    }
                    
                    val activityPackage = if (fullActivityName.contains('.')) {
                        fullActivityName.substringBeforeLast('.')
                    } else {
                        packageName
                    }
                    
                    if (activityPackage != packageName) {
                        continue
                    }
                    
                    val intentFilters = activity.getElementsByTagName("intent-filter")
                    for (l in 0 until intentFilters.length) {
                        val intentFilter = intentFilters.item(l) as? org.w3c.dom.Element ?: continue
                        val actions = intentFilter.getElementsByTagName("action")
                        var hasAction = false
                        for (m in 0 until actions.length) {
                            val action = actions.item(m) as? org.w3c.dom.Element ?: continue
                            val actionName = action.getAttributeNS("http://schemas.android.com/apk/res/android", "name") ?: ""
                            if (actionName == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" ||
                                actionName == "WATCH_FACE_EDITOR") {
                                hasAction = true
                                break
                            }
                        }
                        
                        if (!hasAction) continue
                        
                        if (minSdkVersion < 30) {
                            val categories = intentFilter.getElementsByTagName("category")
                            var hasCategory = false
                            for (m in 0 until categories.length) {
                                val category = categories.item(m) as? org.w3c.dom.Element ?: continue
                                val categoryName = category.getAttributeNS("http://schemas.android.com/apk/res/android", "name") ?: ""
                                if (categoryName == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
                                    hasCategory = true
                                    break
                                }
                            }
                            if (hasCategory) {
                                matchingActivityFound = true
                                break
                            }
                        } else {
                            matchingActivityFound = true
                            break
                        }
                    }
                    if (matchingActivityFound) break
                }
                
                if (!matchingActivityFound) {
                    val message = if (minSdkVersion < 30) {
                        "An activity with action `com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR` and category `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` in the same package is required for watch face configuration."
                    } else {
                        "An activity with action `com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR` in the same package is required for watch face configuration."
                    }
                    
                    val parser = context.client.getXmlParser()
                    val location = parser.getLocation(file, configMetadata)
                    context.report(ISSUE, location, message)
                }
            }
        }
    }
}
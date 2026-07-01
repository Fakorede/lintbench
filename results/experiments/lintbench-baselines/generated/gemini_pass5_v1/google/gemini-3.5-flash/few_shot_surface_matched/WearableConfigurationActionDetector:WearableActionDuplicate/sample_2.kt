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

  override fun checkMergedProject(context: Context) {
    val mainProject = context.mainProject
    val mergedManifest: org.w3c.dom.Document = mainProject.mergedManifest ?: return

    val servicesWithMetadata = mutableListOf<org.w3c.dom.Element>()
    val services = mergedManifest.getElementsByTagName("service")
    for (i in 0 until services.length) {
      val service = services.item(i) as? org.w3c.dom.Element ?: continue
      val metaDatas = getChildrenByTagName(service, "meta-data")
      for (metaData in metaDatas) {
        val name = getAndroidAttribute(metaData, "name")
        val value = getAndroidAttribute(metaData, "value")
        if (name == "com.google.android.wearable.watchface.wearableConfigurationAction" &&
            value == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR") {
          servicesWithMetadata.add(service)
          break
        }
      }
    }

    val matchingActivities = mutableListOf<org.w3c.dom.Element>()
    val activities = mergedManifest.getElementsByTagName("activity")
    val requireCategory = context.project.minSdkVersion.featureLevel < 30

    for (i in 0 until activities.length) {
      val activity = activities.item(i) as? org.w3c.dom.Element ?: continue
      if (matchesActivity(activity, requireCategory)) {
        matchingActivities.add(activity)
      }
    }

    val manifestFile: java.io.File? = context.project.manifestFiles.firstOrNull() ?: context.file

    if (matchingActivities.size > 1) {
      for (activity in matchingActivities) {
        val location = if (manifestFile != null) {
          context.client.xmlParser.getLocation(manifestFile, activity)
        } else {
          Location.create(context.file)
        }
        context.report(
          ISSUE,
          location,
          "Duplicate watch face configuration activities found"
        )
      }
      return
    }

    if (servicesWithMetadata.isNotEmpty() && matchingActivities.isEmpty()) {
      for (service in servicesWithMetadata) {
        val location = if (manifestFile != null) {
          context.client.xmlParser.getLocation(manifestFile, service)
        } else {
          Location.create(context.file)
        }
        context.report(
          ISSUE,
          location,
          "A watch face service defines wearableConfigurationAction, but no matching activity was found."
        )
      }
    }

    if (matchingActivities.isNotEmpty() && servicesWithMetadata.isEmpty()) {
      for (activity in matchingActivities) {
        val location = if (manifestFile != null) {
          context.client.xmlParser.getLocation(manifestFile, activity)
        } else {
          Location.create(context.file)
        }
        context.report(
          ISSUE,
          location,
          "Wearable configuration activity defined but no watch face service has the corresponding metadata."
        )
      }
    }
  }

  private fun getChildrenByTagName(parent: org.w3c.dom.Element, name: String): List<org.w3c.dom.Element> {
    val list = mutableListOf<org.w3c.dom.Element>()
    val children = parent.childNodes
    for (i in 0 until children.length) {
      val child = children.item(i)
      if (child is org.w3c.dom.Element && child.tagName == name) {
        list.add(child)
      }
    }
    return list
  }

  private fun getAndroidAttribute(element: org.w3c.dom.Element, localName: String): String {
    return element.getAttributeNS("http://schemas.android.com/apk/res/android", localName)
      .takeIf { it.isNotEmpty() }
      ?: element.getAttribute("android:$localName")
  }

  private fun matchesActivity(activity: org.w3c.dom.Element, requireCategory: Boolean): Boolean {
    val intentFilters = getChildrenByTagName(activity, "intent-filter")
    for (intentFilter in intentFilters) {
      val actions = getChildrenByTagName(intentFilter, "action")
      val hasAction = actions.any {
        getAndroidAttribute(it, "name") == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR"
      }
      if (hasAction) {
        if (!requireCategory) {
          return true
        }
        val categories = getChildrenByTagName(intentFilter, "category")
        val hasCategory = categories.any {
          getAndroidAttribute(it, "name") == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        }
        if (hasCategory) {
          return true
        }
      }
    }
    return false
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "WearableActionDuplicate",
      briefDescription = "Duplicate watch face configuration activities found",
      explanation = "If and only if a watch face service defines `wearableConfigurationAction` metadata, " +
        "with the value `WATCH_FACE_EDITOR`, there should be an activity in the same package, " +
        "which has an intent filter for `WATCH_FACE_EDITOR` " +
        "(with com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION if minSdkVersion is less than 30).",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(
        WearableConfigurationActionDetector::class.java,
        Scope.MANIFEST_SCOPE
      )
    )
  }
}
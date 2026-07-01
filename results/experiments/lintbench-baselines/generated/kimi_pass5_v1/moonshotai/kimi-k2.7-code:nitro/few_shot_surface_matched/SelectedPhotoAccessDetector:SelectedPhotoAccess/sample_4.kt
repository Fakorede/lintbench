package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*

private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
private const val TAG_USES_PERMISSION = "uses-permission"
private const val ATTR_NAME = "name"
private const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
private const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

  override fun checkMergedProject(context: Context) {
    val project = context.mainProject
    if (project.targetSdk < 34) {
      return
    }

    val document = project.mergeManifest ?: return
    val root = document.documentElement ?: return
    val permissions = root.getElementsByTagName(TAG_USES_PERMISSION)
    for (i in 0 until permissions.length) {
      val element = permissions.item(i) as? org.w3c.dom.Element ?: continue
      val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
      if (name == READ_MEDIA_IMAGES || name == READ_MEDIA_VIDEO) {
        context.report(
          ISSUE,
          element,
          context.getLocation(element),
          "On Android 14 (API 34) and higher, users can grant partial access to their " +
          "photo library when an app requests READ_MEDIA_IMAGES or READ_MEDIA_VIDEO. " +
          "Instead of letting the system manage the selection lifecycle, adapt your app " +
          "to handle partial access to the photo library."
        )
      }
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "SelectedPhotoAccess",
      briefDescription = "Behavior change when requesting photo library access",
      explanation = "Selected Photo Access is a new ability for users to share partial " +
        "access to their photo library when apps request access to their device storage " +
        "on Android 14+. Instead of letting the system manage the selection lifecycle, " +
        "we recommend you adapt your app to handle partial access to the photo library.",
      moreInfo = "https://developer.android.com/about/versions/14/changes/partial-photo-video-access",
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(
        SelectedPhotoAccessDetector::class.java,
        Scope.MANIFEST_SCOPE
      )
    )
  }
}
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

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

  override fun checkMergedProject(context: Context) {
    val manifest = context.mainProject.mergedManifest ?: return
    val document = context.client.xmlParser.parseXml(manifest, context.mainProject) ?: return
    val permissions = document.getElementsByTagName(TAG_USES_PERMISSION)
    for (i in 0 until permissions.length) {
      val node = permissions.item(i) ?: continue
      val attr = node.attributes?.getNamedItemNS(ANDROID_URI, ATTR_NAME) ?: continue
      val name = attr.nodeValue ?: continue
      if (name !in PHOTO_PERMISSIONS) continue
      context.report(
        ISSUE,
        Location.create(manifest),
        "Selected Photo Access is available on Android 14+ when requesting access to the photo library. " +
        "Instead of letting the system manage the selection lifecycle, adapt your app to handle partial access to the photo library."
      )
      return
    }
  }

  companion object {
    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    private const val ATTR_NAME = "name"
    private const val TAG_USES_PERMISSION = "uses-permission"

    private val PHOTO_PERMISSIONS = setOf(
      "android.permission.READ_MEDIA_IMAGES",
      "android.permission.READ_MEDIA_VIDEO"
    )

    @JvmField
    val ISSUE = Issue.create(
      id = "SelectedPhotoAccess",
      briefDescription = "Selected Photo Access behavior change",
      explanation = "On Android 14 and higher, when an app requests READ_MEDIA_IMAGES or READ_MEDIA_VIDEO, " +
        "users can choose to share partial access to their photo library. Instead of letting the system manage " +
        "the selection lifecycle, adapt your app to handle partial access to the photo library; for example, " +
        "use the photo picker or refresh available media after the permission result. " +
        "See https://developer.android.com/about/versions/14/changes/partial-photo-video-access.",
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(SelectedPhotoAccessDetector::class.java, Scope.MANIFEST_SCOPE)
    )
  }
}
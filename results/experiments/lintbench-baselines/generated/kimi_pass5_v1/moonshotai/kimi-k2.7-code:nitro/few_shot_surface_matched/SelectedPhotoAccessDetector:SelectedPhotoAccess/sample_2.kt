package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
private const val ATTR_NAME = "name"
private const val TAG_USES_PERMISSION = "uses-permission"
private const val TAG_USES_PERMISSION_SDK_23 = "uses-permission-sdk-23"

private const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
private const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
private const val READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"
private const val READ_MEDIA_VISUAL_USER_SELECTED =
  "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

private const val ANDROID_14 = 34

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

  override fun checkMergedProject(context: XmlContext) {
    val targetSdk = context.project.targetSdk
    if (targetSdk < ANDROID_14) {
      return
    }

    val root = context.document.documentElement ?: return
    val nodeList = root.getElementsByTagName("*")

    var requestsPhotoAccess = false
    var declaresUserSelected = false
    var offendingElement: org.w3c.dom.Element? = null

    for (i in 0 until nodeList.length) {
      val element = nodeList.item(i) as? org.w3c.dom.Element ?: continue
      val tag = element.tagName
      if (tag != TAG_USES_PERMISSION && tag != TAG_USES_PERMISSION_SDK_23) {
        continue
      }

      val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
      when (name) {
        READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_EXTERNAL_STORAGE -> {
          requestsPhotoAccess = true
          if (offendingElement == null) {
            offendingElement = element
          }
        }
        READ_MEDIA_VISUAL_USER_SELECTED -> {
          declaresUserSelected = true
        }
      }
    }

    if (requestsPhotoAccess && !declaresUserSelected) {
      val locationElement = offendingElement ?: root
      context.report(
        ISSUE,
        locationElement,
        context.getElementLocation(locationElement),
        "Selected Photo Access is available on Android 14+ devices; declare the " +
          "READ_MEDIA_VISUAL_USER_SELECTED permission and adapt your app to handle " +
          "partial access to the photo library"
      )
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
        "declare the READ_MEDIA_VISUAL_USER_SELECTED permission and adapt your app to " +
        "handle partial access to the photo library. Consider using the photo picker if " +
        "your app only needs occasional access to photos and videos.",
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
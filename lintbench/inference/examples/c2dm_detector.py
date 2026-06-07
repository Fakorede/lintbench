SOURCE = '''
// EXAMPLE: C2dmDetector (Kotlin, XmlScanner — manifest element check)
// Issue: UsingC2DM
// Explanation: The C2DM library does not work on Android P or newer devices;
// migrate to Firebase Cloud Messaging.

package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_RECEIVER
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.XmlUtils.getFirstSubTagByName
import com.android.utils.XmlUtils.getNextTagByName
import org.w3c.dom.Element

class C2dmDetector : Detector(), XmlScanner {

  override fun getApplicableElements(): Collection<String> {
    return listOf(TAG_RECEIVER)
  }

  override fun visitElement(context: XmlContext, element: Element) {
    val attribute = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME) ?: return
    val receiverName = attribute.value
    if (receiverName != "com.google.android.c2dm.C2DMBroadcastReceiver" &&
        receiverName != "com.google.android.gcm.GCMBroadcastReceiver") {
      return
    }

    var haveReceive = false
    var haveRegistration = false
    var intentFilter = getFirstSubTagByName(element, TAG_INTENT_FILTER)
    while (intentFilter != null) {
      var action = getFirstSubTagByName(intentFilter, TAG_ACTION)
      while (action != null) {
        val actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (actionName == "com.google.android.c2dm.intent.RECEIVE") haveReceive = true
        else if (actionName == "com.google.android.c2dm.intent.REGISTRATION") haveRegistration = true
        action = getNextTagByName(action, TAG_ACTION)
      }
      intentFilter = getNextTagByName(intentFilter, TAG_INTENT_FILTER)
    }

    if (haveReceive && haveRegistration) {
      context.report(ISSUE, attribute, context.getValueLocation(attribute),
        "The C2DM library does not work on Android P or newer devices; " +
        "migrate to Firebase Cloud Messaging")
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "UsingC2DM",
      briefDescription = "Using C2DM",
      explanation = "The C2DM library does not work on Android P or newer devices; " +
        "migrate to Firebase Cloud Messaging to ensure reliable message delivery.",
      category = Category.SECURITY,
      priority = 8,
      severity = Severity.ERROR,
      implementation = Implementation(C2dmDetector::class.java, Scope.MANIFEST_SCOPE),
    )
  }
}
'''.strip()

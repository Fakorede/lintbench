package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class ForegroundServicePermissionDetector : ResourceXmlDetector(), SourceCodeScanner, XmlScanner {

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val TAG_SERVICE = "service"
        private const val ATTR_FOREGROUND_SERVICE_TYPE = "foregroundServiceType"
        private const val TARGET_SDK_THRESHOLD = 34

        private val TYPE_TO_PERMISSION = mapOf(
            "camera" to "android.permission.FOREGROUND_SERVICE_CAMERA",
            "connectedDevice" to "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
            "dataSync" to "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
            "health" to "android.permission.FOREGROUND_SERVICE_HEALTH",
            "location" to "android.permission.FOREGROUND_SERVICE_LOCATION",
            "mediaPlayback" to "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
            "mediaProjection" to "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
            "microphone" to "android.permission.FOREGROUND_SERVICE_MICROPHONE",
            "phoneCall" to "android.permission.FOREGROUND_SERVICE_PHONE_CALL",
            "remoteMessaging" to "android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING",
            "systemExempted" to "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = "For targetSdkVersion >= 34, each foregroundServiceType listed in the <service> element requires specific sets of permissions to be declared in the manifest. If permissions are missing, then when the foreground service is started with a foregroundServiceType that has missing permissions, a SecurityException will be thrown.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ForegroundServicePermissionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_SERVICE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != TAG_SERVICE) return

        val targetSdk = context.mainProject.targetSdkVersion ?: 0
        if (targetSdk < TARGET_SDK_THRESHOLD) return

        val typeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE) ?: return
        val typeValue = typeAttr.value
        if (typeValue.isEmpty() || typeValue == "none") return

        val types = typeValue.split("|").map { it.trim() }
        val requiredPermissions = mutableListOf("android.permission.FOREGROUND_SERVICE")

        for (type in types) {
            TYPE_TO_PERMISSION[type]?.let { requiredPermissions.add(it) }
        }

        val message = "Missing required permission(s) for foregroundServiceType: ${requiredPermissions.joinToString(", ")}"
        context.report(ISSUE, typeAttr, context.getValueLocation(typeAttr), message)
    }
}
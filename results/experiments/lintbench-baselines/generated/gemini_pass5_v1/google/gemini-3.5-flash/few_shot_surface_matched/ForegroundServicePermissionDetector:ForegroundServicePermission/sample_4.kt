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

    override fun getApplicableElements(): Collection<String> {
        return listOf("service")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != "service") return

        val targetSdk = context.project.targetSdkVersion.apiLevel
        if (targetSdk < 34) return

        val fgsTypeAttr = element.getAttributeNodeNS(ANDROID_URI, "foregroundServiceType") ?: return
        val fgsTypes = fgsTypeAttr.value.split('|').map { it.trim() }

        val declaredPermissions = mutableSetOf<String>()
        val permissions = element.ownerDocument.getElementsByTagName("uses-permission")
        for (i in 0 until permissions.length) {
            val permElement = permissions.item(i) as Element
            val name = permElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name.isNotEmpty()) {
                declaredPermissions.add(name)
            }
        }

        for (type in fgsTypes) {
            val requiredPermission = FGS_TYPE_TO_PERMISSION[type] ?: continue
            if (!declaredPermissions.contains(requiredPermission)) {
                context.report(
                    ISSUE,
                    fgsTypeAttr,
                    context.getValueLocation(fgsTypeAttr),
                    "foregroundServiceType \"$type\" requires \"$requiredPermission\" permission"
                )
            }
        }
    }

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val ATTR_NAME = "name"

        private val FGS_TYPE_TO_PERMISSION = mapOf(
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
            "specialUse" to "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
            "systemExempted" to "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = """
                For targetSdkVersion >= 34, each `foregroundServiceType` listed in the `<service>` element \
                requires specific sets of permissions to be declared in the manifest. If permissions are \
                missing, then when the foreground service is started with a `foregroundServiceType` that has \
                missing permissions, a `SecurityException` will be thrown.
            """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(ForegroundServicePermissionDetector::class.java, Scope.MANIFEST_SCOPE)
        )
    }
}
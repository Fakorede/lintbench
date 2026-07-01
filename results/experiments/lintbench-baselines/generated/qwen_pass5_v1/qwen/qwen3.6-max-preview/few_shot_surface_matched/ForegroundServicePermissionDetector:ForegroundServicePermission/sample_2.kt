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
        private const val API_34 = 34

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
            "specialUse" to "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = "For targetSdkVersion >= 34, each foregroundServiceType listed in the <service> element requires specific permissions to be declared in the manifest. Missing permissions will cause a SecurityException when the service is started.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(ForegroundServicePermissionDetector::class.java, Scope.MANIFEST_SCOPE)
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("service")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdk = context.project.targetSdkVersion
        if (targetSdk == null || targetSdk < API_34) return

        val fgTypeAttr = element.getAttributeNodeNS(ANDROID_URI, "foregroundServiceType") ?: return
        val fgTypeValue = fgTypeAttr.value
        if (fgTypeValue.isEmpty() || fgTypeValue.startsWith("@")) return

        val types = fgTypeValue.split("|").map { it.trim() }
        val declaredPermissions = getDeclaredPermissions(context)

        for (type in types) {
            val requiredPermission = TYPE_TO_PERMISSION[type] ?: continue
            if (!declaredPermissions.contains(requiredPermission)) {
                context.report(
                    ISSUE,
                    fgTypeAttr,
                    context.getValueLocation(fgTypeAttr),
                    "Missing permission $requiredPermission required by foregroundServiceType \"$type\""
                )
            }
        }
    }

    private fun getDeclaredPermissions(context: XmlContext): Set<String> {
        val permissions = mutableSetOf<String>()
        val document = context.document ?: return permissions
        val elements = document.getElementsByTagName("uses-permission")
        for (i in 0 until elements.length) {
            val permElement = elements.item(i) as? Element ?: continue
            val name = permElement.getAttributeNS(ANDROID_URI, "name")
            if (name.isNotEmpty()) {
                permissions.add(name)
            }
        }
        return permissions
    }
}
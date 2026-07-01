package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import java.util.EnumSet
import org.w3c.dom.Element

class ForegroundServicePermissionDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ForegroundServicePermissionDetector::class.java,
            EnumSet.of(Scope.MANIFEST),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = """
                For apps with targetSdkVersion >= 34, each foregroundServiceType listed in the \
                `<service>` element requires specific sets of permissions to be declared in the manifest. \
                If these permissions are missing, starting the foreground service will throw a SecurityException.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        private val typeToPermission = mapOf(
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
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("manifest")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != "manifest") return

        val targetSdkVersion = context.project.targetSdkVersion.featureLevel
        if (targetSdkVersion < 34) {
            return
        }

        val declaredPermissions = mutableSetOf<String>()
        val usesPermissions = element.getElementsByTagName("uses-permission")
        for (i in 0 until usesPermissions.length) {
            val perm = usesPermissions.item(i) as? Element ?: continue
            val name = perm.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
            if (name.isNotEmpty()) {
                declaredPermissions.add(name)
            }
        }

        val services = element.getElementsByTagName("service")
        for (i in 0 until services.length) {
            val service = services.item(i) as? Element ?: continue
            val typeAttr = service.getAttributeNS("http://schemas.android.com/apk/res/android", "foregroundServiceType")
            if (typeAttr.isNotEmpty()) {
                val types = typeAttr.split('|').map { it.trim() }
                for (type in types) {
                    val requiredPermission = typeToPermission[type]
                    if (requiredPermission != null && !declaredPermissions.contains(requiredPermission)) {
                        val attrNode = service.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "foregroundServiceType")
                        val location = if (attrNode != null) context.getLocation(attrNode) else context.getLocation(service)
                        context.report(
                            ISSUE,
                            service,
                            location,
                            "Missing `$requiredPermission` required by foregroundServiceType `$type`"
                        )
                    }
                }
            }
        }
    }
}
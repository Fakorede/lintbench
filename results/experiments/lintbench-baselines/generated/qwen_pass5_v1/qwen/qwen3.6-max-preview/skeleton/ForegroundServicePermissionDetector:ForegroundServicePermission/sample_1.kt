package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
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
        private val ANDROID_URI = "http://schemas.android.com/apk/res/android"

        private val IMPLEMENTATION = Implementation(
            ForegroundServicePermissionDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = "For targetSdkVersion >= 34, each foregroundServiceType listed in the <service> element requires specific sets of permissions to be declared in the manifest. If permissions are missing, then when the foreground service is started with a foregroundServiceType that has missing permissions, a SecurityException will be thrown.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        private val TYPE_PERMISSIONS = mapOf(
            "camera" to listOf("android.permission.CAMERA", "android.permission.FOREGROUND_SERVICE_CAMERA"),
            "connectedDevice" to listOf("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"),
            "dataSync" to listOf("android.permission.FOREGROUND_SERVICE_DATA_SYNC"),
            "health" to listOf("android.permission.FOREGROUND_SERVICE_HEALTH"),
            "location" to listOf("android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION", "android.permission.FOREGROUND_SERVICE_LOCATION"),
            "mediaPlayback" to listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"),
            "mediaProjection" to listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"),
            "microphone" to listOf("android.permission.RECORD_AUDIO", "android.permission.FOREGROUND_SERVICE_MICROPHONE"),
            "phoneCall" to listOf("android.permission.FOREGROUND_SERVICE_PHONE_CALL"),
            "remoteMessaging" to listOf("android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING"),
            "specialUse" to listOf("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"),
            "systemExempted" to listOf("android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED")
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("service")

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.file.name != "AndroidManifest.xml") return
        if ((context.project.targetSdkVersion ?: 0) < 34) return

        val fgTypeAttr = element.getAttributeNS(ANDROID_URI, "foregroundServiceType")
        if (fgTypeAttr.isNullOrEmpty() || fgTypeAttr.startsWith("@")) return

        val declaredPermissions = getDeclaredPermissions(context)
        val types = fgTypeAttr.split("|").map { it.trim() }

        for (type in types) {
            val required = TYPE_PERMISSIONS[type] ?: continue
            val missing = required.filter { !declaredPermissions.contains(it) }
            if (missing.isNotEmpty()) {
                val message = "Foreground service type '$type' requires the following permissions: ${missing.joinToString(", ")}. " +
                        "Add them to the manifest to avoid SecurityException on Android 14+."
                context.report(ISSUE, context.getLocation(element), message)
            }
        }
    }

    private fun getDeclaredPermissions(context: XmlContext): Set<String> {
        val permissions = mutableSetOf<String>()
        val root = context.document?.documentElement ?: return permissions
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.nodeName == "uses-permission") {
                val name = node.getAttributeNS(ANDROID_URI, "name")
                if (name.isNotEmpty()) {
                    permissions.add(name)
                }
            }
        }
        return permissions
    }
}
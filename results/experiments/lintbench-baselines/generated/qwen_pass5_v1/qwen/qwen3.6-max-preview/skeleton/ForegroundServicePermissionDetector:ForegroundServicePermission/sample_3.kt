package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
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
            explanation = "For targetSdkVersion >= 34, each foregroundServiceType listed in the <service> element requires specific sets of permissions to be declared in the manifest. If permissions are missing, then when the foreground service is started with a foregroundServiceType that has missing permissions, a SecurityException will be thrown.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        private val FGS_TYPE_PERMISSIONS = mapOf(
            "camera" to listOf("android.permission.CAMERA"),
            "connectedDevice" to listOf(
                "android.permission.BLUETOOTH_CONNECT",
                "android.permission.BLUETOOTH_ADVERTISE",
                "android.permission.BLUETOOTH_SCAN"
            ),
            "health" to listOf(
                "android.permission.BODY_SENSORS",
                "android.permission.BODY_SENSORS_BACKGROUND"
            ),
            "location" to listOf(
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.ACCESS_FINE_LOCATION"
            ),
            "microphone" to listOf("android.permission.RECORD_AUDIO"),
            "phoneCall" to listOf(
                "android.permission.CALL_PHONE",
                "android.permission.READ_PHONE_STATE",
                "android.permission.READ_PHONE_NUMBERS",
                "android.permission.ANSWER_PHONE_CALLS",
                "android.permission.READ_CALL_LOG"
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("service")

    override fun visitElement(context: XmlContext, element: Element) {
        if ((context.project.targetSdkVersion ?: 0) < 34) return

        val attr = element.getAttributeNode("foregroundServiceType") ?: return
        val value = attr.value
        if (value.isBlank()) return

        val declaredPermissions = getDeclaredPermissions(context)
        val types = value.split("|")
            .map { it.trim().removePrefix("android:") }
            .filter { it.isNotEmpty() }

        for (type in types) {
            val required = FGS_TYPE_PERMISSIONS[type] ?: continue
            val hasRequired = required.any { perm ->
                declaredPermissions.contains(perm) ||
                    declaredPermissions.contains(perm.removePrefix("android.permission."))
            }
            if (!hasRequired) {
                val message = "Foreground service type \"$type\" requires one of the following " +
                    "permissions: ${required.joinToString()}. Add the missing permission to the manifest."
                context.report(ISSUE, element, context.getLocation(attr), message)
            }
        }
    }

    private fun getDeclaredPermissions(context: XmlContext): Set<String> {
        val permissions = mutableSetOf<String>()
        val root = context.document.documentElement ?: return permissions
        val nodes = root.getElementsByTagName("uses-permission")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            val name = el.getAttribute("android:name")
            if (name.isNotEmpty()) {
                permissions.add(name)
            }
        }
        return permissions
    }
}
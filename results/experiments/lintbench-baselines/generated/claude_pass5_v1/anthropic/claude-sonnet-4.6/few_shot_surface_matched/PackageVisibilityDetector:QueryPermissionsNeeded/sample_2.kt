package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.targetSdkAtLeast
import com.android.utils.XmlUtils.getFirstSubTagByName
import com.android.utils.XmlUtils.getNextTagByName
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

    // -----------------------------------------------------------------------
    // XmlScanner – detect broad intent-filter actions in the manifest that
    // may need <queries> declarations on Android 11+
    // -----------------------------------------------------------------------

    override fun getApplicableElements(): Collection<String> = listOf(TAG_ACTION)

    override fun visitElement(context: XmlContext, element: Element) {
        val nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME) ?: return
        val actionName = nameAttr.value

        // We only care about actions inside an <intent-filter> that is itself
        // inside a <queries> child – actually we want to flag usages of broad
        // implicit intents that are *sent* (i.e. in intent-filters of other
        // components) without a corresponding <queries> block.  The simpler
        // and more actionable check here is: if an app declares an
        // <intent-filter> with a broad action that is known to be affected by
        // package visibility filtering, warn the developer.
        if (actionName !in FILTERED_INTENT_ACTIONS) return

        // Walk up to verify we are inside an <intent-filter> element.
        val parent = element.parentNode as? Element ?: return
        if (parent.tagName != TAG_INTENT_FILTER) return

        val location = context.getValueLocation(nameAttr)
        val message =
            "Apps targeting Android 11+ that query for apps using this action may need " +
                "to add a `<queries>` declaration in their manifest. " +
                "See https://g.co/dev/packagevisibility for details."
        val incident = Incident(ISSUE, element, location, message)
        context.report(incident, targetSdkAtLeast(ANDROID_11_API_LEVEL))
    }

    // -----------------------------------------------------------------------
    // SourceCodeScanner – detect calls to PackageManager methods that are
    // affected by package-visibility filtering on Android 11+
    // -----------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> = AFFECTED_METHOD_NAMES

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, PACKAGE_MANAGER_CLASS)) return

        val methodName = method.name
        val (message, key) = when (methodName) {
            "getInstalledPackages" ->
                Pair(
                    "`PackageManager.getInstalledPackages` will not return information about all " +
                        "installed packages when targeting Android 11+. Consider using " +
                        "`PackageManager.getPackageInfo` for specific packages or adding a " +
                        "`<queries>` declaration to your manifest. " +
                        "See https://g.co/dev/packagevisibility for details.",
                    "getInstalledPackages"
                )
            "getInstalledApplications" ->
                Pair(
                    "`PackageManager.getInstalledApplications` will not return information about " +
                        "all installed apps when targeting Android 11+. Consider using " +
                        "`PackageManager.getPackageInfo` for specific packages or adding a " +
                        "`<queries>` declaration to your manifest. " +
                        "See https://g.co/dev/packagevisibility for details.",
                    "getInstalledApplications"
                )
            "queryIntentActivities",
            "queryIntentServices",
            "queryIntentContentProviders",
            "queryBroadcastReceivers" ->
                Pair(
                    "`PackageManager.$methodName` results may be incomplete when targeting " +
                        "Android 11+ unless you add the appropriate `<queries>` entries in your " +
                        "manifest. See https://g.co/dev/packagevisibility for details.",
                    methodName
                )
            "resolveActivity",
            "resolveService",
            "resolveContentProvider" ->
                Pair(
                    "`PackageManager.$methodName` may return null when targeting Android 11+ if " +
                        "the target package is not visible. Consider adding a `<queries>` " +
                        "declaration in your manifest. " +
                        "See https://g.co/dev/packagevisibility for details.",
                    methodName
                )
            "getPackageInfo",
            "getApplicationInfo" ->
                Pair(
                    "`PackageManager.$methodName` may throw `NameNotFoundException` when " +
                        "targeting Android 11+ if the target package is not visible. Consider " +
                        "adding a `<queries>` declaration in your manifest. " +
                        "See https://g.co/dev/packagevisibility for details.",
                    methodName
                )
            else -> return
        }

        val location = context.getLocation(node)
        val incident = Incident(ISSUE, node, location, message)
        // Store the method key so filterIncident can inspect it if needed.
        context.report(incident, map().put(KEY_METHOD, key).also { targetSdkAtLeast(ANDROID_11_API_LEVEL) })
        context.report(incident, targetSdkAtLeast(ANDROID_11_API_LEVEL))
    }

    override fun filterIncident(context: com.android.tools.lint.detector.api.Context, incident: Incident, map: LintMap): Boolean {
        // Only report the incident when the app targets Android 11 (API 30) or higher.
        val mainProject = context.mainProject
        val targetSdk = mainProject.targetSdk
        if (targetSdk < ANDROID_11_API_LEVEL) {
            return false
        }
        return true
    }

    companion object {
        private const val ANDROID_11_API_LEVEL = 30
        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"
        private const val KEY_METHOD = "method"

        private val AFFECTED_METHOD_NAMES = listOf(
            "getInstalledPackages",
            "getInstalledApplications",
            "queryIntentActivities",
            "queryIntentServices",
            "queryIntentContentProviders",
            "queryBroadcastReceivers",
            "resolveActivity",
            "resolveService",
            "resolveContentProvider",
            "getPackageInfo",
            "getApplicationInfo"
        )

        /**
         * Intent actions that are commonly used to query for other apps and are
         * affected by package-visibility filtering on Android 11+.
         */
        private val FILTERED_INTENT_ACTIONS = setOf(
            "android.intent.action.MAIN",
            "android.intent.action.VIEW",
            "android.intent.action.SEND",
            "android.intent.action.SENDTO",
            "android.intent.action.PICK",
            "android.intent.action.GET_CONTENT",
            "android.media.action.IMAGE_CAPTURE",
            "android.media.action.VIDEO_CAPTURE",
            "android.intent.action.CALL",
            "android.intent.action.DIAL",
            "android.intent.action.EDIT",
            "android.intent.action.DELETE",
            "android.intent.action.INSTALL_PACKAGE",
            "android.intent.action.UNINSTALL_PACKAGE"
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation =
                """
                Apps that target Android 11 cannot query or interact with other installed apps \
                by default. If you need to query or interact with other installed apps, you may need \
                to add a `<queries>` declaration in your manifest.

                As a corollary, the methods `PackageManager#getInstalledPackages` and \
                `PackageManager#getInstalledApplications` will no longer return information about all \
                installed apps. To query specific apps or types of apps, you can use methods like \
                `PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities`.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST),
                Scope.JAVA_FILE_SCOPE,
                Scope.MANIFEST_SCOPE
            ),
            androidSpecific = true,
            moreInfo = "https://g.co/dev/packagevisibility"
        )
    }
}
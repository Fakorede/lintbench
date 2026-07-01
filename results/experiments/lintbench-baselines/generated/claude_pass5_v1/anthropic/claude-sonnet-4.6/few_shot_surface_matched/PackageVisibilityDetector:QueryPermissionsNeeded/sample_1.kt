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
    // XmlScanner – detect broad implicit-intent actions in the manifest that
    // may require <queries> declarations on API 30+.
    // -----------------------------------------------------------------------

    override fun getApplicableElements(): Collection<String> = listOf(TAG_ACTION)

    override fun visitElement(context: XmlContext, element: Element) {
        val actionName = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: return
        if (actionName.isEmpty()) return

        // Only care about actions inside an <intent-filter> that is itself
        // inside a <queries> block – we don't flag those.  We flag broad
        // implicit-intent actions declared inside <intent-filter> children of
        // elements other than <queries> children, because the developer may
        // need to add a matching <queries> declaration when targeting API 30+.
        //
        // For the purpose of this detector we flag well-known "broad" actions
        // that are commonly used to discover other apps.
        if (!BROAD_IMPLICIT_ACTIONS.contains(actionName)) return

        val intentFilter = element.parentNode as? Element ?: return
        if (intentFilter.tagName != TAG_INTENT_FILTER) return

        // If this <intent-filter> is already inside a <queries> element we
        // don't need to warn – the developer has already declared it.
        val grandParent = intentFilter.parentNode as? Element ?: return
        if (grandParent.tagName == "queries") return

        val location = context.getValueLocation(
            element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME) ?: return
        )
        val incident = Incident(
            ISSUE,
            element,
            location,
            "Consider adding a `<queries>` declaration for action `$actionName` " +
                "if you need to resolve apps that handle it on Android 11+",
        )
        context.report(incident, targetSdkAtLeast(TARGET_API))
    }

    // -----------------------------------------------------------------------
    // SourceCodeScanner – detect calls to PackageManager methods that return
    // information about all installed packages (affected by package visibility
    // filtering on API 30+).
    // -----------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> = FLAGGED_METHODS

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInSubClassOf(method, PACKAGE_MANAGER_CLASS)) return

        val methodName = method.name
        val message = when (methodName) {
            "getInstalledPackages", "getInstalledApplications" ->
                "`PackageManager#$methodName` returns fewer results on apps targeting " +
                    "Android 11 (API $TARGET_API)+. Consider using " +
                    "`PackageManager#getPackageInfo` or " +
                    "`PackageManager#queryIntentActivities` with a `<queries>` " +
                    "declaration in your manifest. " +
                    "See https://g.co/dev/packagevisibility for details."

            "queryIntentActivities", "queryIntentServices",
            "queryIntentContentProviders", "queryBroadcastReceivers" ->
                "`PackageManager#$methodName` may return fewer results on apps targeting " +
                    "Android 11 (API $TARGET_API)+ unless matching apps are visible via " +
                    "a `<queries>` declaration. " +
                    "See https://g.co/dev/packagevisibility for details."

            "resolveActivity", "resolveService", "resolveContentProvider" ->
                "`PackageManager#$methodName` may return null on apps targeting " +
                    "Android 11 (API $TARGET_API)+ unless the target is visible via " +
                    "a `<queries>` declaration. " +
                    "See https://g.co/dev/packagevisibility for details."

            "getPackageInfo", "getApplicationInfo" ->
                "`PackageManager#$methodName` will throw `NameNotFoundException` for " +
                    "apps that are not visible on Android 11 (API $TARGET_API)+. " +
                    "Ensure the target app is declared in a `<queries>` element. " +
                    "See https://g.co/dev/packagevisibility for details."

            else ->
                "`PackageManager#$methodName` is affected by package-visibility " +
                    "filtering on Android 11 (API $TARGET_API)+. " +
                    "See https://g.co/dev/packagevisibility for details."
        }

        val incident = Incident(ISSUE, node, context.getLocation(node), message)
        context.report(incident, targetSdkAtLeast(TARGET_API))
    }

    // -----------------------------------------------------------------------
    // filterIncident – store extra data so that conditional checks work.
    // -----------------------------------------------------------------------

    override fun filterIncident(context: com.android.tools.lint.detector.api.Context, incident: Incident, map: LintMap): Boolean {
        // We use targetSdkAtLeast() constraint already; returning true here
        // means "include this incident in the conditional-check pipeline".
        return true
    }

    // -----------------------------------------------------------------------
    // Companion
    // -----------------------------------------------------------------------

    companion object {
        private const val TARGET_API = 30
        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"

        private val FLAGGED_METHODS = listOf(
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
            "getApplicationInfo",
        )

        /**
         * Well-known implicit-intent actions that, when queried without a
         * matching `<queries>` entry, will return no results on API 30+.
         */
        private val BROAD_IMPLICIT_ACTIONS = setOf(
            "android.intent.action.SEND",
            "android.intent.action.SENDTO",
            "android.intent.action.SEND_MULTIPLE",
            "android.intent.action.VIEW",
            "android.intent.action.EDIT",
            "android.intent.action.PICK",
            "android.intent.action.GET_CONTENT",
            "android.intent.action.OPEN_DOCUMENT",
            "android.intent.action.CREATE_DOCUMENT",
            "android.media.action.IMAGE_CAPTURE",
            "android.media.action.VIDEO_CAPTURE",
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation =
                """
                Apps that target Android 11 cannot query or interact with other installed \
                apps by default. If you need to query or interact with other installed apps, \
                you may need to add a `<queries>` declaration in your manifest.

                As a corollary, the methods `PackageManager#getInstalledPackages` and \
                `PackageManager#getInstalledApplications` will no longer return information \
                about all installed apps. To query specific apps or types of apps, you can \
                use methods like `PackageManager#getPackageInfo` or \
                `PackageManager#queryIntentActivities`.

                See https://g.co/dev/packagevisibility for details.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST),
                Scope.JAVA_FILE_SCOPE,
                Scope.MANIFEST_SCOPE,
            ),
            androidSpecific = true,
        )
    }
}
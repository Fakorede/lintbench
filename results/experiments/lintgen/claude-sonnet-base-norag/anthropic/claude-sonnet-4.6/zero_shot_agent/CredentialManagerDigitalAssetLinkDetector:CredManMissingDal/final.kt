package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_RESOURCE
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_META_DATA
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element
import java.util.EnumSet

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner, XmlScanner {

    // Per-project state
    private var usesCredentialManager = false
    private var hasDalMetaData = false
    private var manifestLocation: Location? = null
    private var callLocation: Location? = null

    companion object {
        private const val CREDENTIAL_MANAGER_CLASS = "androidx.credentials.CredentialManager"
        private const val GET_CREDENTIAL_METHOD = "getCredential"
        private const val GET_CREDENTIAL_ASYNC_METHOD = "getCredentialAsync"
        private const val PASSWORD_CREDENTIAL_CLASS = "androidx.credentials.PasswordCredential"
        private const val GET_PASSWORD_OPTION_CLASS = "androidx.credentials.GetPasswordOption"
        private const val CREATE_PASSWORD_REQUEST_CLASS = "androidx.credentials.CreatePasswordRequest"

        // The meta-data name can be either "asset_statements" or the full
        // "com.google.android.gms.wallet.api.enabled" style; the DAL one is
        // "asset_statements" (without a package prefix).
        private const val ASSET_STATEMENTS_META_DATA_SUFFIX = "asset_statements"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements \
                string resource file that includes the `assetlinks.json` files to load must be \
                declared in the manifest using a `<meta-data>` element.

                Add a `<meta-data>` element inside the `<application>` tag in your \
                `AndroidManifest.xml` with the name `asset_statements` pointing to a string \
                resource that contains the Digital Asset Links JSON.

                See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal \
                for more details.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE),
                EnumSet.of(Scope.MANIFEST),
                EnumSet.of(Scope.JAVA_FILE)
            ),
            moreInfo = "https://developer.android.com/identity/sign-in/credential-manager#add-support-dal"
        )
    }

    // -----------------------------------------------------------------------
    // XmlScanner – manifest scanning
    // -----------------------------------------------------------------------

    override fun getApplicableElements(): Collection<String> = listOf(TAG_META_DATA, TAG_APPLICATION)

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_APPLICATION -> {
                if (manifestLocation == null) {
                    manifestLocation = context.getLocation(element)
                }
            }
            TAG_META_DATA -> {
                val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: return
                // Accept both "asset_statements" and any name ending with ".asset_statements"
                if (name == ASSET_STATEMENTS_META_DATA_SUFFIX ||
                    name.endsWith(".$ASSET_STATEMENTS_META_DATA_SUFFIX")
                ) {
                    val resource = element.getAttributeNS(ANDROID_URI, ATTR_RESOURCE)
                    if (!resource.isNullOrBlank()) {
                        hasDalMetaData = true
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // SourceCodeScanner – Java/Kotlin scanning
    // -----------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> = listOf(
        GET_CREDENTIAL_METHOD,
        GET_CREDENTIAL_ASYNC_METHOD
    )

    override fun getApplicableConstructorTypes(): List<String> = listOf(
        PASSWORD_CREDENTIAL_CLASS,
        GET_PASSWORD_OPTION_CLASS,
        CREATE_PASSWORD_REQUEST_CLASS
    )

    override fun visitMethodCall(context: com.android.tools.lint.detector.api.JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass?.qualifiedName ?: return
        if (containingClass == CREDENTIAL_MANAGER_CLASS) {
            usesCredentialManager = true
            if (callLocation == null) {
                callLocation = context.getLocation(node)
            }
        }
    }

    override fun visitConstructor(
        context: com.android.tools.lint.detector.api.JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        val containingClass = constructor.containingClass?.qualifiedName ?: return
        if (containingClass == PASSWORD_CREDENTIAL_CLASS ||
            containingClass == GET_PASSWORD_OPTION_CLASS ||
            containingClass == CREATE_PASSWORD_REQUEST_CLASS
        ) {
            usesCredentialManager = true
            if (callLocation == null) {
                callLocation = context.getLocation(node)
            }
        }
    }

    // -----------------------------------------------------------------------
    // Report after the whole project has been checked
    // -----------------------------------------------------------------------

    override fun afterCheckEachProject(context: Context) {
        if (usesCredentialManager && !hasDalMetaData) {
            val location = manifestLocation ?: callLocation ?: Location.create(context.file)
            val message = "Missing Digital Asset Link declaration: when using Credential " +
                "Manager password sign-in, add a `<meta-data>` element with " +
                "`android:name=\"asset_statements\"` and a `android:resource` pointing " +
                "to your asset statements string resource inside the `<application>` " +
                "tag of your `AndroidManifest.xml`."
            context.report(Incident(ISSUE, message, location))
        }
        // Reset state for the next project
        usesCredentialManager = false
        hasDalMetaData = false
        manifestLocation = null
        callLocation = null
    }
}
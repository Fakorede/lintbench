package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_META_DATA
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
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

class CredentialManagerDigitalAssetLinkDetector : Detector(), XmlScanner, SourceCodeScanner {

    companion object {
        private const val CREDENTIAL_MANAGER_CLASS =
            "androidx.credentials.CredentialManager"
        private const val GET_CREDENTIAL_METHOD = "getCredential"
        private const val GET_CREDENTIAL_ASYNC_METHOD = "getCredentialAsync"
        private const val PASSWORD_CREDENTIAL_CLASS =
            "androidx.credentials.PasswordCredential"
        private const val GET_PASSWORD_OPTION_CLASS =
            "androidx.credentials.GetPasswordOption"
        private const val GET_CREDENTIAL_REQUEST_CLASS =
            "androidx.credentials.GetCredentialRequest"

        private const val META_DATA_ASSET_STATEMENTS =
            "asset_statements"
        private const val META_DATA_ASSET_STATEMENTS_FULL =
            "androidx.credentials.ASSET_STATEMENTS"

        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements \
                string resource file that includes the `assetlinks.json` files to load must be \
                declared in the manifest using a `<meta-data>` element.

                Add a `<meta-data>` element inside the `<application>` tag in your \
                `AndroidManifest.xml` that points to a string resource containing the Digital \
                Asset Links JSON. For example:

                ```xml
                <meta-data
                    android:name="asset_statements"
                    android:value="@string/asset_statements" />
                ```

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

    // Whether the project uses Credential Manager password sign-in APIs
    private var usesCredentialManagerPasswordSignIn = false

    // Whether the manifest has the required asset_statements meta-data
    private var hasAssetStatementsMetaData = false

    // Location to report the issue (application element in manifest)
    private var manifestApplicationLocation: Location? = null

    // Location from source code usage
    private var credentialManagerUsageLocation: Location? = null

    // -------------------------------------------------------------------------
    // XmlScanner — manifest inspection
    // -------------------------------------------------------------------------

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_APPLICATION, TAG_META_DATA)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_APPLICATION -> {
                if (context.document.documentElement == element.ownerDocument.documentElement) {
                    manifestApplicationLocation = context.getLocation(element)
                }
            }
            TAG_META_DATA -> {
                val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: return
                if (name == META_DATA_ASSET_STATEMENTS ||
                    name == META_DATA_ASSET_STATEMENTS_FULL ||
                    name.contains("asset_statements", ignoreCase = true)
                ) {
                    hasAssetStatementsMetaData = true
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // SourceCodeScanner — detect Credential Manager password sign-in usage
    // -------------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> {
        return listOf(GET_CREDENTIAL_METHOD, GET_CREDENTIAL_ASYNC_METHOD)
    }

    override fun visitMethodCall(
        context: com.android.tools.lint.detector.api.JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val containingClass = method.containingClass?.qualifiedName ?: return
        if (containingClass != CREDENTIAL_MANAGER_CLASS) return

        // Check if any argument or the broader call context involves password credentials
        // We flag any getCredential call on CredentialManager as potentially using passwords
        // since we can't always statically determine the request contents.
        // A more precise check would inspect GetCredentialRequest arguments.
        usesCredentialManagerPasswordSignIn = true
        if (credentialManagerUsageLocation == null) {
            credentialManagerUsageLocation = context.getLocation(node)
        }
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(
            PASSWORD_CREDENTIAL_CLASS,
            GET_PASSWORD_OPTION_CLASS
        )
    }

    override fun visitConstructor(
        context: com.android.tools.lint.detector.api.JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        usesCredentialManagerPasswordSignIn = true
        if (credentialManagerUsageLocation == null) {
            credentialManagerUsageLocation = context.getLocation(node)
        }
    }

    // -------------------------------------------------------------------------
    // Project-level hook — report after full project analysis
    // -------------------------------------------------------------------------

    override fun afterCheckRootProject(context: Context) {
        if (usesCredentialManagerPasswordSignIn && !hasAssetStatementsMetaData) {
            val location = credentialManagerUsageLocation
                ?: manifestApplicationLocation
                ?: Location.create(context.project.dir)

            context.report(
                issue = ISSUE,
                location = location,
                message = "Missing Digital Asset Link declaration: when using Credential Manager " +
                    "for password sign-in, you must declare an `asset_statements` " +
                    "`<meta-data>` element in your `AndroidManifest.xml` pointing to a " +
                    "string resource that lists the `assetlinks.json` files to load. " +
                    "See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal"
            )
        }
    }
}
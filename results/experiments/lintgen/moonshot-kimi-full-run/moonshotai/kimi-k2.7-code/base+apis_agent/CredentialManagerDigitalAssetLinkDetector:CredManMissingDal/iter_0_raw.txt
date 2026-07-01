package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_RESOURCE
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_STRING
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element

class CredentialManagerDigitalAssetLinkDetector : Detector(), Detector.XmlScanner, Detector.SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements
                string resource file that includes the `assetlinks.json` files to load must
                be declared in the manifest using a `<meta-data>` element.
            """.trimIndent(),
            moreInfo = "https://developer.android.com/identity/sign-in/credential-manager#add-support-dal",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
                Scope.MANIFEST_SCOPE,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val ASSET_STATEMENTS = "asset_statements"
        private const val ASSET_LINKS_FILE = "assetlinks.json"
        private const val CREDENTIAL_MANAGER = "androidx.credentials.CredentialManager"
    }

    private val credentialManagerCalls = mutableListOf<Location>()
    private val stringResources = mutableMapOf<String, String>()
    private var manifestHasAssetStatements = false
    private var assetStatementStringName: String? = null
    private var manifestMetaDataLocation: Location? = null

    override fun beforeCheckEachProject(context: Context) {
        credentialManagerCalls.clear()
        stringResources.clear()
        manifestHasAssetStatements = false
        assetStatementStringName = null
        manifestMetaDataLocation = null
    }

    override fun getApplicableElements(): Collection<String> = listOf(TAG_META_DATA, TAG_STRING)

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_META_DATA -> {
                val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val resource = element.getAttributeNS(ANDROID_URI, ATTR_RESOURCE)
                if (name == ASSET_STATEMENTS && resource.startsWith("@string/")) {
                    manifestHasAssetStatements = true
                    assetStatementStringName = resource.substringAfter("@string/")
                    manifestMetaDataLocation = context.getLocation(element)
                }
            }
            TAG_STRING -> {
                val name = element.getAttribute("name").takeIf { it.isNotBlank() } ?: return
                val value = element.textContent?.trim() ?: return
                stringResources[name] = value
            }
        }
    }

    override fun getApplicableMethodNames(): List<String> = listOf("getCredential", "createCredential")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.evaluator.extendsClass(method.containingClass, CREDENTIAL_MANAGER, true)) {
            credentialManagerCalls.add(context.getLocation(node))
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (credentialManagerCalls.isEmpty()) return

        if (!manifestHasAssetStatements) {
            val location = manifestMetaDataLocation ?: credentialManagerCalls.first()
            context.report(
                ISSUE,
                location,
                "Credential Manager is used but the manifest is missing the required " +
                    "<meta-data android:name=\"$ASSET_STATEMENTS\" android:resource=\"@string/...\" /> declaration."
            )
            return
        }

        val stringName = assetStatementStringName
        if (stringName == null) {
            context.report(
                ISSUE,
                manifestMetaDataLocation ?: credentialManagerCalls.first(),
                "The $ASSET_STATEMENTS meta-data must reference a @string resource."
            )
            return
        }

        val statement = stringResources[stringName]
        if (statement == null || !statement.contains(ASSET_LINKS_FILE, ignoreCase = true)) {
            context.report(
                ISSUE,
                manifestMetaDataLocation ?: credentialManagerCalls.first(),
                "The '$stringName' string resource referenced by the $ASSET_STATEMENTS " +
                    "meta-data must include the assetlinks.json files to load."
            )
        }
    }
}
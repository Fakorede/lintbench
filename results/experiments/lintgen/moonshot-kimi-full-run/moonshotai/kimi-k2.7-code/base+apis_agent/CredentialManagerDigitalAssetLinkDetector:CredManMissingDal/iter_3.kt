package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_RESOURCE
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_STRING
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element
import java.util.EnumSet

class CredentialManagerDigitalAssetLinkDetector : Detector(), Detector.XmlScanner, SourceCodeScanner {

    companion object {
        @JvmField
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
                EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST, Scope.RESOURCE_FILE)
            )
        )

        private const val ASSET_STATEMENTS = "asset_statements"
        private const val ASSET_LINKS_FILE = "assetlinks.json"
        private const val CREDENTIAL_MANAGER = "androidx.credentials.CredentialManager"
    }

    private val credentialManagerCalls = mutableListOf<Location>()
    private val stringResources = mutableMapOf<String, String>()
    private val stringResourceLocations = mutableMapOf<String, Location>()
    private var manifestHasAssetStatementsMetaData = false
    private var manifestHasValidResource = false
    private var assetStatementStringName: String? = null
    private var manifestMetaDataLocation: Location? = null

    override fun beforeCheckEachProject(context: Context) {
        credentialManagerCalls.clear()
        stringResources.clear()
        stringResourceLocations.clear()
        manifestHasAssetStatementsMetaData = false
        manifestHasValidResource = false
        assetStatementStringName = null
        manifestMetaDataLocation = null
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.VALUES

    override fun getApplicableElements(): Collection<String> = listOf(TAG_META_DATA, TAG_STRING)

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_META_DATA -> {
                val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name == ASSET_STATEMENTS) {
                    manifestHasAssetStatementsMetaData = true
                    manifestMetaDataLocation = context.getLocation(element)
                    val resource = element.getAttributeNS(ANDROID_URI, ATTR_RESOURCE)
                    if (resource.startsWith("@string/")) {
                        manifestHasValidResource = true
                        assetStatementStringName = resource.substringAfter("@string/")
                    }
                }
            }
            TAG_STRING -> {
                val name = element.getAttribute("name").takeIf { it.isNotBlank() } ?: return
                val value = element.textContent?.trim() ?: return
                stringResources[name] = value
                stringResourceLocations[name] = context.getLocation(element)
            }
        }
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        "getCredential",
        "createCredential",
        "prepareGetCredential",
        "getCredentialAsync",
        "createCredentialAsync",
        "prepareGetCredentialAsync"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        if (context.evaluator.extendsClass(containingClass, CREDENTIAL_MANAGER, false)) {
            credentialManagerCalls.add(context.getLocation(node))
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (credentialManagerCalls.isEmpty()) return

        if (!manifestHasAssetStatementsMetaData) {
            val location = manifestMetaDataLocation ?: credentialManagerCalls.first()
            context.report(
                ISSUE,
                location,
                "Credential Manager is used but the manifest is missing the required " +
                    "<meta-data android:name=\"$ASSET_STATEMENTS\" " +
                    "android:resource=\"@string/...\" /> declaration."
            )
            return
        }

        if (!manifestHasValidResource) {
            context.report(
                ISSUE,
                manifestMetaDataLocation ?: credentialManagerCalls.first(),
                "The $ASSET_STATEMENTS meta-data must reference a string resource."
            )
            return
        }

        val stringName = assetStatementStringName
        if (stringName == null) {
            context.report(
                ISSUE,
                manifestMetaDataLocation ?: credentialManagerCalls.first(),
                "The $ASSET_STATEMENTS meta-data must reference a string resource."
            )
            return
        }

        val statement = stringResources[stringName]
        if (statement == null) {
            context.report(
                ISSUE,
                manifestMetaDataLocation ?: credentialManagerCalls.first(),
                "The string resource @string/$stringName referenced by the $ASSET_STATEMENTS " +
                    "meta-data could not be found."
            )
            return
        }

        val normalized = statement.replace("\\\"", "\"").trim()
        val includes = Regex("\"include\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .findAll(normalized)

        if (!includes.any()) {
            val location = stringResourceLocations[stringName]
                ?: manifestMetaDataLocation
                ?: credentialManagerCalls.first()
            context.report(
                ISSUE,
                location,
                "The $ASSET_STATEMENTS string resource must contain an include entry " +
                    "referencing an $ASSET_LINKS_FILE file."
            )
            return
        }

        val allValid = includes.all { match ->
            val url = match.groupValues[1]
            url.contains(ASSET_LINKS_FILE, ignoreCase = true) && url.contains("://")
        }

        if (!allValid) {
            val location = stringResourceLocations[stringName]
                ?: manifestMetaDataLocation
                ?: credentialManagerCalls.first()
            context.report(
                ISSUE,
                location,
                "The include entry in the $ASSET_STATEMENTS string resource must reference " +
                    "a URL to an $ASSET_LINKS_FILE file."
            )
        }
    }
}
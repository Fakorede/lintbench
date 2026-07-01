package com.android.tools.lint.checks

import com.android.SdkConstants
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
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element
import java.util.EnumSet

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner, XmlScanner {

    private var hasCredentialManagerUsage = false
    private var hasAssetStatements = false
    private var manifestLocation: Location? = null

    override fun beforeCheckEachProject(context: Context) {
        hasCredentialManagerUsage = false
        hasAssetStatements = false
        manifestLocation = null
    }

    // --- XmlScanner ---

    override fun getApplicableElements(): Collection<String> {
        return listOf("meta-data", "application")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.file.name != SdkConstants.FN_ANDROID_MANIFEST_XML) {
            return
        }

        if (element.tagName == "meta-data") {
            val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (name == "asset_statements") {
                hasAssetStatements = true
            }
        } else if (element.tagName == "application") {
            manifestLocation = context.getNameLocation(element)
        }
    }

    // --- SourceCodeScanner ---

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getCredential", "createCredential", "clearCredentialState")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass?.qualifiedName ?: return
        if (containingClass.startsWith("androidx.credentials.CredentialManager") ||
            containingClass.startsWith("android.credentials.CredentialManager")
        ) {
            hasCredentialManagerUsage = true
        }
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(
            "androidx.credentials.GetCredentialRequest",
            "androidx.credentials.CreatePasswordRequest",
            "androidx.credentials.CreatePublicKeyCredentialRequest"
        )
    }

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        hasCredentialManagerUsage = true
    }

    override fun afterCheckEachProject(context: Context) {
        if (hasCredentialManagerUsage && !hasAssetStatements) {
            val location = manifestLocation ?: run {
                val manifestFile = context.project.manifestFiles.firstOrNull() ?: return
                Location.create(manifestFile)
            }
            context.report(
                ISSUE,
                location,
                "Missing Digital Asset Link declaration in AndroidManifest.xml"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements string resource file \
                that includes the `assetlinks.json` files to load must be declared in the manifest using a \
                `<meta-data>` element.
                """,
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            )
        )
    }
}
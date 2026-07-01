package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element
import java.util.EnumSet

class CredentialManagerDigitalAssetLinkDetector : Detector(), Detector.UastScanner, Detector.XmlScanner {

    private var usesCredentialManager = false
    private var credentialManagerLocation: Location? = null
    private var hasAssetStatements = false

    override fun getApplicableMethodNames(): List<String>? = listOf(
        "create", "getCredential", "GetPasswordOption", "GetCredentialRequest",
        "build", "addOption", "setPasswordOption", "GetCreatePasswordRequest"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val cls = method.containingClass?.qualifiedName ?: return
        if (cls.startsWith("androidx.credentials.")) {
            usesCredentialManager = true
            if (credentialManagerLocation == null) {
                credentialManagerLocation = context.getLocation(node)
            }
        }
    }

    override fun getApplicableElements(): Collection<String>? = listOf(SdkConstants.TAG_APPLICATION)

    override fun visitElement(context: XmlContext, element: Element) {
        if (hasAssetStatements) return
        val metaDatas = element.getElementsByTagName(SdkConstants.TAG_META_DATA)
        for (i in 0 until metaDatas.length) {
            val meta = metaDatas.item(i) as? Element ?: continue
            val name = meta.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (name == "asset_statements") {
                hasAssetStatements = true
                return
            }
        }
    }

    override fun afterCheckProject(context: Context) {
        if (usesCredentialManager && !hasAssetStatements) {
            val location = credentialManagerLocation ?: Location.create(context.project.dir)
            context.report(
                ISSUE,
                location,
                "Missing Digital Asset Link configuration for Credential Manager. " +
                "Add `<meta-data android:name=\"asset_statements\" android:resource=\"@string/asset_statements\" />` " +
                "to your AndroidManifest.xml `<application>` tag."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = "When using password sign-in through Credential Manager, an asset statements string resource file " +
                "that includes the `assetlinks.json` files to load must be declared in the manifest using a `<meta-data>` element.\n\n" +
                "Reference: https://developer.android.com/identity/sign-in/credential-manager#add-support-dal",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST)
            )
        )
    }
}
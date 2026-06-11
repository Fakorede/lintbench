package com.android.tools.lint.checks

import com.android.SdkConstants.*
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "MissingCredentialManagerDigitalAssetLink",
            briefDescription = "When using password sign-in through Credential Manager, an asset statements string resource file that includes the `assetlinks.json` files to load must be declared in the manifest using a `<meta-data>` element.",
            explanation = """
                <p>Using password sign-in with Credential Manager requires declaring an asset statements string resource file that includes the `assetlinks.json` files to load. This should be done by adding a `<meta-data>` element in your AndroidManifest.xml.</p>
                <p>This check ensures that you have declared this meta-data correctly.</p>
            """,
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true,
            moreInfo = "https://developer.android.com/identity/sign-in/credential-manager#add-support-dal"
        )
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("getPasswordSignInCredentialRequestOptions")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (method.name == "getPasswordSignInCredentialRequestOptions") {
            val manifest = context.getManifest()
            if (manifest != null && !hasDigitalAssetLinkMetaTag(manifest)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Missing Digital Asset Link for Credential Manager"
                )
            }
        }
    }

    private fun hasDigitalAssetLinkMetaTag(document: XmlDocument): Boolean {
        val applicationNode = document.root?.firstChild
        if (applicationNode != null) {
            var child = applicationNode.firstChild
            while (child != null) {
                if ("meta-data" == child.tagName) {
                    val nameAttr = child.getAttribute("name", "")
                    val valueAttr = child.getAttribute("resource", "")
                    if ("asset_statements".equals(nameAttr, ignoreCase = true)) {
                        return true
                    }
                }
                child = child.nextSibling
            }
        }
        return false
    }

}
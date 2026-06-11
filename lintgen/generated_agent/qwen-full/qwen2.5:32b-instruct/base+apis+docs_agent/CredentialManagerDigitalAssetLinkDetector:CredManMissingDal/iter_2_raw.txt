package com.android.tools.lint.checks

import com.android.SdkConstants.*
import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner {

    companion object {
        private val ISSUE = Issue.create(
            id = "MissingCredentialManagerDigitalAssetLinks",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements string resource file that includes the `assetlinks.json` files to load must be declared in the manifest using a `<meta-data>` element.
                
                Reference documentation:
                - https://developer.android.com/identity/sign-in/credential-manager#add-support-dal
            """,
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("getSignInCredentialRequestOptions")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.evaluator.isMemberInClass(method, "com.google.android.gms.credentials.CredentialManager")) {
            val manifest = context.getManifest()
            if (manifest != null && !hasDigitalAssetLinksMetaTag(manifest)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Missing Digital Asset Link for Credential Manager"
                )
            }
        }
    }

    private fun hasDigitalAssetLinksMetaTag(manifest: XmlDocument): Boolean {
        val applicationNode = manifest.getApplication()
        if (applicationNode == null) return false

        val metaDataNodes = applicationNode.getElementsByTagName(TAG_META_DATA)

        for (i in 0 until metaDataNodes.length) {
            val metaData = metaDataNodes.item(i)
            val nameAttr = metaData.getAttributeNS(null, ATTRIBUTE_NAME)
            val valueAttr = metaData.getAttributeNS(null, ATTRIBUTE_VALUE)

            if (nameAttr?.contains("asset_statements") == true && valueAttr != null) {
                return true
            }
        }

        return false
    }
}
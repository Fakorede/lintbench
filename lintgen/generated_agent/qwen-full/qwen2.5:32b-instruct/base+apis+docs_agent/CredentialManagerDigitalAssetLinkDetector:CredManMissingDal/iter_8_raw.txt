package com.android.tools.lint.checks

import com.android.SdkConstants.*
import com.android.annotations.NonNull
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.w3c.dom.Node
import org.jetbrains.uast.UCallExpression

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

        private const val META_DATA_NAME = "asset_statements"
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("getSignInCredentialRequestOptions")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.evaluator.isMemberInClass(method, "com.google.android.gms.credentials.CredentialManager")) {
            val manifestFile = context.xmlDocument
            if (manifestFile != null && !hasDigitalAssetLinksMetaTag(manifestFile)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Missing Digital Asset Link for Credential Manager"
                )
            }
        }
    }

    private fun hasDigitalAssetLinksMetaTag(@NonNull manifestDocument: Node): Boolean {
        val applicationNode = manifestDocument.ownerDocument.getElementsByTagName(TAG_APPLICATION).item(0)
        if (applicationNode == null) return false

        val metaDataNodes = applicationNode.ownerDocument.getElementsByTagName(TAG_META_DATA)

        for (i in 0 until metaDataNodes.length) {
            val metaData = metaDataNodes.item(i)
            val nameAttr = metaData.attributes.getNamedItem("name")?.nodeValue
            val valueAttr = metaData.attributes.getNamedItem("value")?.nodeValue

            if (nameAttr?.contains(META_DATA_NAME) == true && valueAttr != null) {
                return true
            }
        }

        return false
    }
}
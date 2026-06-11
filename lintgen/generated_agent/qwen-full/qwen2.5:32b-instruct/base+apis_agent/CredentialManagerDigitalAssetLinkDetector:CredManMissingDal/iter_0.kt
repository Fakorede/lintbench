package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Node

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            "MissingCredentialManagerDigitalAssetLink",
            "When using password sign-in through Credential Manager, an asset statements string resource file that includes the `assetlinks.json` files to load must be declared in the manifest using a `<meta-data>` element.",
            "https://developer.android.com/identity/sign-in/credential-manager#add-support-dal"
        )
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("getPasswordSignInCredentialRequestOptions")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (method.name == "getPasswordSignInCredentialRequestOptions") {
            val manifest = context.manifest
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

    private fun hasDigitalAssetLinkMetaTag(manifest: Node): Boolean {
        val applicationNode = manifest.ownerDocument.getElementsByTagName("application").item(0)
        if (applicationNode != null) {
            val metaNodes = applicationNode.ownerDocument.getElementsByTagName("meta-data")
            for (i in 0 until metaNodes.length) {
                val metaNode = metaNodes.item(i)
                val nameAttr = metaNode.attributes.getNamedItem("name")
                val valueAttr = metaNode.attributes.getNamedItem("resource")
                if (nameAttr != null && valueAttr != null &&
                    "asset_statements".equals(nameAttr.nodeValue, ignoreCase = true)) {
                    return true
                }
            }
        }
        return false
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolveMethod() as PsiMethod?
                if (method != null && method.name == "getPasswordSignInCredentialRequestOptions") {
                    val manifest = context.manifest
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
        }
    }

}
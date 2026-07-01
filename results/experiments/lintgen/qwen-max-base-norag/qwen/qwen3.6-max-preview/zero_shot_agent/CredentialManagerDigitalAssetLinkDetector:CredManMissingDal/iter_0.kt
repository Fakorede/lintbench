package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import com.android.utils.XmlUtils
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Element
import java.io.File

class CredentialManagerDigitalAssetLinkDetector : Detector(), Detector.UastScanner {

    private val usageLocations = mutableListOf<Location>()

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UReferenceExpression::class.java, UCallExpression::class.java)

    override fun visitReferenceExpression(context: JavaContext, node: UReferenceExpression) {
        val qualifiedName = node.qualifiedName ?: return
        if (isCredentialManagerApi(qualifiedName)) {
            usageLocations.add(context.getLocation(node))
        }
    }

    override fun visitCallExpression(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val qualifiedName = method.containingClass?.qualifiedName ?: return
        if (isCredentialManagerApi(qualifiedName)) {
            usageLocations.add(context.getLocation(node))
        }
    }

    private fun isCredentialManagerApi(qualifiedName: String): Boolean =
        qualifiedName.startsWith("androidx.credentials.CredentialManager") ||
        qualifiedName.startsWith("androidx.credentials.GetPasswordOption")

    override fun afterCheckProject(context: Context) {
        if (usageLocations.isEmpty()) return

        val manifestFile = context.mainProject.manifest ?: return
        if (!manifestFile.exists()) return

        if (!hasAssetStatementsMetaData(manifestFile)) {
            context.report(
                ISSUE,
                usageLocations.first(),
                "Missing Digital Asset Link configuration for Credential Manager. " +
                "Add `<meta-data android:name=\"asset_statements\" android:resource=\"@string/asset_statements\" />` " +
                "to your AndroidManifest.xml `<application>` tag."
            )
        }
    }

    private fun hasAssetStatementsMetaData(manifestFile: File): Boolean {
        val document = try {
            XmlUtils.parseDocument(manifestFile, true)
        } catch (e: Exception) {
            return false
        }

        val application = document.documentElement
            ?.getElementsByTagName("application")
            ?.item(0) as? Element ?: return false

        val metaDatas = application.getElementsByTagName("meta-data")
        for (i in 0 until metaDatas.length) {
            val element = metaDatas.item(i) as? Element ?: continue
            val name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name")
            if (name == "asset_statements") {
                return true
            }
        }
        return false
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
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
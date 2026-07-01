package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiClass
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.USimpleNameReferenceExpression

class CredentialManagerDigitalAssetLinkDetector : Detector(), Detector.UastScanner {

    private var usesCredentialManager = false

    override fun beforeCheckProject(context: Context) {
        usesCredentialManager = false
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(
            UCallExpression::class.java,
            USimpleNameReferenceExpression::class.java,
            UImportStatement::class.java
        )
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitImportStatement(node: UImportStatement) {
                if (usesCredentialManager) return
                val importRef = node.importReference ?: return
                val resolvedName = importRef.asSourceString()
                if (resolvedName.startsWith("androidx.credentials")) {
                    usesCredentialManager = true
                }
            }

            override fun visitCallExpression(node: UCallExpression) {
                if (usesCredentialManager) return
                val method = node.resolve() ?: return
                val cls = method.containingClass ?: return
                val qualifiedName = cls.qualifiedName ?: return
                if (qualifiedName.startsWith("androidx.credentials.")) {
                    usesCredentialManager = true
                }
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                if (usesCredentialManager) return
                val resolved = node.resolve() as? PsiClass ?: return
                val qualifiedName = resolved.qualifiedName ?: return
                if (qualifiedName.startsWith("androidx.credentials.")) {
                    usesCredentialManager = true
                }
            }
        }
    }

    override fun afterCheckProject(context: Context) {
        if (!usesCredentialManager) return

        val manifestFile = context.project.manifestFiles.firstOrNull() ?: return
        val hasAssetStatements = checkManifestForAssetStatements(context, manifestFile)
        if (!hasAssetStatements) {
            val location = Location.create(manifestFile)
            context.report(
                ISSUE,
                location,
                "Missing Digital Asset Link (asset_statements) in AndroidManifest.xml for Credential Manager"
            )
        }
    }

    private fun checkManifestForAssetStatements(context: Context, manifestFile: java.io.File): Boolean {
        try {
            val document = context.client.getXmlDocument(manifestFile) ?: return false
            val root = document.documentElement ?: return false
            val applications = root.getElementsByTagName("application")
            for (i in 0 until applications.length) {
                val application = applications.item(i) as? org.w3c.dom.Element ?: continue
                val children = application.childNodes
                for (j in 0 until children.length) {
                    val child = children.item(j) as? org.w3c.dom.Element ?: continue
                    if (child.tagName == "meta-data") {
                        val name = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                        if (name == "asset_statements") {
                            return true
                        }
                        if (child.getAttribute("android:name") == "asset_statements") {
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore XML parsing exceptions
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements string resource file \
                that includes the `assetlinks.json` files to load must be declared in the manifest \
                using a `<meta-data>` element.
                
                To fix this, add the `<meta-data android:name="asset_statements" android:resource="@string/asset_statements" />` \
                element inside the `<application>` tag of your `AndroidManifest.xml`.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
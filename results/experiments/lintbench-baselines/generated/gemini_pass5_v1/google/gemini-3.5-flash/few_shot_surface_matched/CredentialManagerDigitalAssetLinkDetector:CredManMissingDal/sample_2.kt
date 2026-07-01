package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner {

  private var usesPasswordLocal = false

  companion object {
    private const val KEY_USES_PASSWORD = "usesPassword"

    @JvmField
    val ISSUE =
      Issue.create(
        id = "CredManMissingDal",
        briefDescription = "Missing Digital Asset Link for Credential Manager",
        explanation =
          """
                When using password sign-in through Credential Manager, an asset statements string resource file that includes the `assetlinks.json` files to load must be declared in the manifest using a `<meta-data>` element.
            """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation = Implementation(CredentialManagerDigitalAssetLinkDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableConstructorTypes(): List<String> {
    return listOf(
      "androidx.credentials.GetPasswordOption",
      "androidx.credentials.CreatePasswordRequest"
    )
  }

  override fun visitConstructor(
    context: JavaContext,
    node: UCallExpression,
    constructor: PsiMethod
  ) {
    usesPasswordLocal = true
    context.getPartialResults(ISSUE).map().put(KEY_USES_PASSWORD, true)
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResult) {
    var usesPassword = false
    for (project in partialResults.projects()) {
      val map = partialResults.map(project)
      if (map.getBoolean(KEY_USES_PASSWORD) == true) {
        usesPassword = true
        break
      }
    }
    if (usesPassword) {
      checkManifestAndReport(context)
    }
  }

  override fun afterCheckRootProject(context: Context) {
    if (context.driver.isPartialAnalysis()) {
      return
    }
    if (usesPasswordLocal) {
      checkManifestAndReport(context)
    }
  }

  private fun checkManifestAndReport(context: Context) {
    val mainProject = context.mainProject
    if (!mainProject.isAndroidProject) {
      return
    }
    val mergedManifest = mainProject.mergedManifest
    val hasDal = mergedManifest?.let { hasAssetStatements(it) } ?: false
    if (!hasDal) {
      val location = context.getLocation(mainProject)
      val incident = Incident(
        ISSUE,
        "To support password sign-in with Credential Manager, you must declare a Digital Asset Link in your manifest using a <meta-data> element with android:name=\"asset_statements\".",
        location
      )
      context.report(incident)
    }
  }

  private fun hasAssetStatements(manifest: org.w3c.dom.Document): Boolean {
    val metaDatas = manifest.getElementsByTagName("meta-data")
    for (i in 0 until metaDatas.length) {
      val element = metaDatas.item(i) as? org.w3c.dom.Element ?: continue
      val name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
      if (name == "asset_statements") {
        return true
      }
      val nameAttr = element.getAttribute("android:name")
      if (nameAttr == "asset_statements") {
        return true
      }
    }
    return false
  }
}
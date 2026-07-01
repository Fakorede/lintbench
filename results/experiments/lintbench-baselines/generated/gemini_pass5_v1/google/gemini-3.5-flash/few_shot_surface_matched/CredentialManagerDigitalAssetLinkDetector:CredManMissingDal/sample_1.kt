package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.SourcePosition
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val CRED_MAN_MISSING_DAL =
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

  private val usages = mutableListOf<Usage>()
  private var reported = false

  private class Usage(val file: java.io.File, val start: Int, val end: Int)

  override fun getApplicableConstructorTypes(): List<String> {
    return listOf(
      "androidx.credentials.GetPasswordOption",
      "androidx.credentials.CreatePasswordRequest"
    )
  }

  override fun visitConstructor(
    context: JavaContext,
    node: UCallExpression,
    candidate: PsiMethod
  ) {
    val location = context.getLocation(node)
    val file = location.file
    val start = location.start?.offset ?: -1
    val end = location.end?.offset ?: -1
    usages.add(Usage(file, start, end))

    val map = context.getPartialResults(CRED_MAN_MISSING_DAL).map(context.project)
    val count = map.getInt("count") ?: 0
    map.put("file_$count", file.absolutePath)
    map.put("start_$count", start)
    map.put("end_$count", end)
    map.put("count", count + 1)
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResult) {
    if (reported) return

    val manifest = context.mainProject.mergedManifest ?: return
    val root = manifest.documentElement ?: return
    val metaDatas = root.getElementsByTagName("meta-data")
    var hasAssetStatements = false
    for (i in 0 until metaDatas.length) {
      val element = metaDatas.item(i) as? org.w3c.dom.Element ?: continue
      val name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
      if (name == "asset_statements" || element.getAttribute("android:name") == "asset_statements") {
        hasAssetStatements = true
        break
      }
    }

    if (!hasAssetStatements) {
      reported = true
      for (project in partialResults.projects()) {
        val map = partialResults.map(project)
        val count = map.getInt("count") ?: 0
        for (i in 0 until count) {
          val filePath = map.getString("file_$i") ?: continue
          val start = map.getInt("start_$i") ?: -1
          val end = map.getInt("end_$i") ?: -1
          val file = java.io.File(filePath)
          val location = if (start != -1 && end != -1) {
            Location.create(file, SourcePosition(start, 0, start), SourcePosition(end, 0, end))
          } else {
            Location.create(file)
          }
          val incident = Incident(
            CRED_MAN_MISSING_DAL,
            location,
            "Missing Digital Asset Link in AndroidManifest.xml. When using password sign-in through Credential Manager, you must declare the asset statements in your manifest."
          )
          context.report(incident)
        }
      }
    }
  }

  override fun afterCheckRootProject(context: Context) {
    if (reported) return
    if (usages.isEmpty()) return

    val manifest = context.mainProject.mergedManifest ?: return
    val root = manifest.documentElement ?: return
    val metaDatas = root.getElementsByTagName("meta-data")
    var hasAssetStatements = false
    for (i in 0 until metaDatas.length) {
      val element = metaDatas.item(i) as? org.w3c.dom.Element ?: continue
      val name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
      if (name == "asset_statements" || element.getAttribute("android:name") == "asset_statements") {
        hasAssetStatements = true
        break
      }
    }

    if (!hasAssetStatements) {
      reported = true
      for (usage in usages) {
        val location = if (usage.start != -1 && usage.end != -1) {
          Location.create(usage.file, SourcePosition(usage.start, 0, usage.start), SourcePosition(usage.end, 0, usage.end))
        } else {
          Location.create(usage.file)
        }
        val incident = Incident(
          CRED_MAN_MISSING_DAL,
          location,
          "Missing Digital Asset Link in AndroidManifest.xml. When using password sign-in through Credential Manager, you must declare the asset statements in your manifest."
        )
        context.report(incident)
      }
    }
  }
}
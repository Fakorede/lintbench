SOURCE = '''
// EXAMPLE: ProguardAndroidTxtDetector (Kotlin, GradleScanner — checkMethodCall pattern)
// Issue: ProguardAndroidTxtUsage
// Explanation: getDefaultProguardFile('proguard-android.txt') disables R8 optimizations;
// use proguard-android-optimize.txt instead.

package com.android.tools.lint.checks

import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity

class ProguardAndroidTxtDetector : Detector(), GradleScanner {
  override fun checkMethodCall(
    context: GradleContext,
    statement: String,
    parent: String?,
    parentParent: String?,
    namedArguments: Map<String, String>,
    unnamedArguments: List<String>,
    cookie: Any,
  ) {
    // Only apply to application plugin, since we really care more about `-dontoptimize`
    // at level of app optimization
    if (context.project.isLibrary) return

    if (statement == "getDefaultProguardFile") {
      if (
        unnamedArguments.any { it.contains("proguard-android.txt") } || namedArguments.values.any { it.contains("proguard-android.txt") }
      ) {
        val incident =
          Incident(
            ISSUE,
            cookie,
            context.getLocation(cookie),
            "Avoid `getDefaultProguardFile('proguard-android.txt')`",
            fix().replace().pattern("proguard-android.txt").with("proguard-android-optimize.txt").build(),
          )
        if ((context.project.gradleModelVersion?.major ?: 9) < 9 && !LintClient.isStudio) {
          // Downgrade to warning if not in Studio, since this is very bad for perf, but app will still run
          // Note this is only prior to AGP 9, at which point the value isn't supported.
          incident.overrideSeverity(Severity.WARNING)
        }
        context.client.report(context, incident)
      }
    }
  }

  companion object {
    val ISSUE =
      Issue.create(
        id = "ProguardAndroidTxtUsage",
        briefDescription = "Use proguard-android-optimize.txt to enable optimizations",
        explanation =
          "Support for `getDefaultProguardFile('proguard-android.txt')` will be removed in AGP 9.0" +
            " since it includes `-dontoptimize`, which prevents R8 from performing many" +
            " optimizations. Instead use" +
            " `getDefaultProguardFile('proguard-android-optimize.txt)`, and if needed," +
            " temporarily use `-dontoptimize` in a custom keep rule file while fixing breakages.",
        category = Category.PERFORMANCE,
        priority = 2,
        severity = Severity.ERROR,
        implementation = Implementation(ProguardAndroidTxtDetector::class.java, Scope.GRADLE_SCOPE),
        moreInfo = "https://developer.android.com/topic/performance/app-optimization/enable-app-optimization",
        androidSpecific = true,
      )
  }
}
'''.strip()

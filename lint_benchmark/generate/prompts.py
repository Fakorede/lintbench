"""
generate/prompts.py
-------------------
Prompt templates and output extraction for LintBench generation.
"""

import re

# ---------------------------------------------------------------------------
# Few-shot examples — excluded from the benchmark to avoid contamination
# ---------------------------------------------------------------------------

FEW_SHOT_KOTLIN = '''
// EXAMPLE: AddJavascriptInterface detector (Kotlin)
// Issue: addJavascriptInterface Called
// Explanation: For applications built for API levels below 17,
// WebView#addJavascriptInterface presents a security hazard as JavaScript
// on the target web page has the ability to use reflection to access the
// injected object's public fields and thus manipulate the host application.

package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class AddJavascriptInterfaceDetector : Detector(), SourceCodeScanner {
  companion object {
    val ISSUE = Issue.create(
        id = "AddJavascriptInterface",
        briefDescription = "`addJavascriptInterface` Called",
        explanation = """
            For applications built for API levels below 17, `WebView#addJavascriptInterface`
            presents a security hazard as JavaScript on the target web page has the ability
            to use reflection to access the injected object's public fields.
            """,
        category = Category.SECURITY,
        priority = 9,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = Implementation(
            AddJavascriptInterfaceDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )
    )
    const val WEB_VIEW = "android.webkit.WebView"
    const val ADD_JAVASCRIPT_INTERFACE = "addJavascriptInterface"
  }

  override fun getApplicableMethodNames(): List<String> =
      listOf(ADD_JAVASCRIPT_INTERFACE)

  override fun visitMethodCall(
      context: JavaContext,
      node: UCallExpression,
      method: PsiMethod
  ) {
    if (context.project.minSdk >= 17) return
    val evaluator = context.evaluator
    if (!evaluator.methodMatches(method, WEB_VIEW, true,
            "java.lang.Object", "java.lang.String")) return
    context.report(
        ISSUE, node, context.getNameLocation(node),
        "`WebView.addJavascriptInterface` should not be called with " +
            "minSdkVersion < 17 for security reasons"
    )
  }
}
'''.strip()

FEW_SHOT_JAVA = '''
// EXAMPLE: SetJavaScriptEnabled detector (Java)
// Issue: SetJavaScriptEnabled
// Explanation: Your code should not invoke setJavaScriptEnabled if you are
// not taking other security precautions. A malicious website could use this
// to run JavaScript that accesses private data or device features.

package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;

public class SetJavaScriptEnabledDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "SetJavaScriptEnabled",
            "Using setJavaScriptEnabled",
            "Your code should not invoke `setJavaScriptEnabled` if you are not " +
            "taking other security precautions. A malicious website could use this " +
            "to run JavaScript that accesses private data or device features.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(SetJavaScriptEnabledDetector.class, Scope.JAVA_FILE_SCOPE));

    private static final String SET_JAVASCRIPT_ENABLED = "setJavaScriptEnabled";

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(SET_JAVASCRIPT_ENABLED);
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression call, PsiMethod method) {
        context.report(ISSUE, call, context.getNameLocation(call),
                "Using `setJavaScriptEnabled` can introduce XSS vulnerabilities " +
                "into your application, review carefully.");
    }
}
'''.strip()


# ---------------------------------------------------------------------------
# Prompt templates
# ---------------------------------------------------------------------------

SYSTEM_PROMPT = """\
You are an expert Android developer specialising in Android Lint custom checks.
You write Lint Detector implementations in {lang} that are correct, idiomatic,
and compile cleanly against the Android Lint API.

Rules:
- Output ONLY the detector source file. No explanation, no markdown fences.
- Use the exact package: com.android.tools.lint.checks
- The class name must match the detector name derived from the issue ID.
- Implement every method listed in the required methods.
- Import only from: com.android.tools.lint.*, com.intellij.psi.*, org.jetbrains.uast.*
- Do NOT include a main() method or any test code.
"""

ZERO_SHOT_TEMPLATE = """\
Implement an Android Lint Detector in {lang} for the following issue.

Issue ID: {issue_id}
Detector class name: {detector}
Language: {lang}
Category: {category}
Severity: {severity}
Scanner interfaces to implement: {scanner_interfaces}

Specification:
{nl_spec}

Required methods to implement (in this order — Lint API overrides first):
{methods_list}
{more_info}
Generate the complete {detector}.{ext} source file now.\
"""

FEW_SHOT_TEMPLATE = """\
Here is an example of a complete Android Lint Detector in {lang}:

{example}

---

Now implement a NEW Android Lint Detector in {lang} for the following issue.

Issue ID: {issue_id}
Detector class name: {detector}
Language: {lang}
Category: {category}
Severity: {severity}
Scanner interfaces to implement: {scanner_interfaces}

Specification:
{nl_spec}

Required methods to implement (in this order — Lint API overrides first):
{methods_list}
{more_info}
Generate the complete {detector}.{ext} source file now.\
"""

COT_TEMPLATE = """\
Implement an Android Lint Detector in {lang} for the following issue.

Issue ID: {issue_id}
Detector class name: {detector}
Language: {lang}
Category: {category}
Severity: {severity}
Scanner interfaces to implement: {scanner_interfaces}

Specification:
{nl_spec}

Required methods to implement (in this order — Lint API overrides first):
{methods_list}
{more_info}
Before writing the code, briefly reason through:
1. Which Lint API entry point to use (e.g. visitMethodCall, visitElement)
2. What AST nodes or patterns to match
3. What condition triggers the report

Then output the complete {detector}.{ext} source file.\
"""


def build_prompt(instance: dict, variant: str) -> tuple[str, str]:
    """Return (system_prompt, user_prompt) for the given instance and variant."""
    lang     = "Kotlin" if instance["check_lang"] == "kt" else "Java"
    ext      = instance["check_lang"]
    issue_id = instance["issue_id"]
    detector = instance["detector"]

    methods_list = "\n".join(
        f"  {i+1}. {m}" for i, m in enumerate(instance["methods_to_generate"])
    )

    more_info = ""
    if instance.get("more_info_urls"):
        urls = "\n".join(f"  - {u}" for u in instance["more_info_urls"])
        more_info = f"\nReference documentation:\n{urls}\n"

    common = dict(
        lang=lang,
        ext=ext,
        issue_id=issue_id,
        detector=detector,
        category=instance["category"],
        severity=instance["severity"],
        scanner_interfaces=", ".join(instance["scanner_interfaces"]) or "SourceCodeScanner",
        nl_spec=instance["nl_spec"],
        methods_list=methods_list,
        more_info=more_info,
    )

    system = SYSTEM_PROMPT.format(lang=lang)

    if variant == "zero_shot":
        user = ZERO_SHOT_TEMPLATE.format(**common)
    elif variant == "few_shot":
        example = FEW_SHOT_KOTLIN if ext == "kt" else FEW_SHOT_JAVA
        user = FEW_SHOT_TEMPLATE.format(example=example, **common)
    elif variant == "cot":
        user = COT_TEMPLATE.format(**common)
    else:
        raise ValueError(f"Unknown prompt variant: {variant}")

    return system, user


def extract_code(raw: str, ext: str) -> str:
    """
    Extract source code from the model's response.
    Handles: raw code, ```kotlin/java/... fences, plain ``` fences.
    Falls back to returning raw text if no fence found.
    """
    for lang_tag in (ext, "kotlin" if ext == "kt" else "java", ""):
        pattern = rf"```{lang_tag}\s*\n(.*?)```"
        m = re.search(pattern, raw, re.DOTALL | re.IGNORECASE)
        if m:
            return m.group(1).strip()

    if raw.strip().startswith("package") or raw.strip().startswith("/*"):
        return raw.strip()

    return raw.strip()

package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UastUtils;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for use of HostnameVerifier implementations whose `verify` method "
                    + "always returns true (thus trusting any hostname) which could result in insecure network "
                    + "traffic caused by trusting arbitrary hostnames in TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            5,
            Severity.WARNING,
            IMPLEMENTATION);

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";

    @Nullable
    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList(HOSTNAME_VERIFIER);
    }

    @Override
    public void visitConstructor(@NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod constructor) {
        UClass uClass = UastUtils.getContainingUClass(node);
        if (uClass == null || !uClass.isAnonymous()) {
            return;
        }

        for (PsiMethod method : uClass.getMethods()) {
            if ("verify".equals(method.getName())) {
                if (returnsTrue(method)) {
                    context.report(ISSUE, node, context.getLocation(node),
                            "Verifying hostnames by always returning true is insecure.");
                }
                break;
            }
        }
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("setHostnameVerifier", "setDefaultHostnameVerifier");
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod method) {
        List<UExpression> args = node.getValueArguments();
        if (args.size() == 1) {
            UExpression arg = args.get(0);
            String source = arg.asSourceString();
            if (source != null && source.contains("ALLOW_ALL_HOSTNAME_VERIFIER")) {
                context.report(ISSUE, node, context.getLocation(node),
                        "Using an allow-all hostname verifier is insecure.");
            }
        }
    }

    private static boolean returnsTrue(@NonNull PsiMethod method) {
        PsiCodeBlock body = method.getBody();
        if (body == null) {
            return false;
        }
        PsiStatement[] statements = body.getStatements();
        if (statements.length != 1) {
            return false;
        }
        PsiStatement stmt = statements[0];
        if (!(stmt instanceof PsiReturnStatement)) {
            return false;
        }
        PsiExpression retVal = ((PsiReturnStatement) stmt).getReturnValue();
        if (retVal instanceof PsiLiteralExpression) {
            Object value = ((PsiLiteralExpression) retVal).getValue();
            return Boolean.TRUE.equals(value);
        }
        if (retVal instanceof PsiReferenceExpression) {
            PsiElement resolved = ((PsiReferenceExpression) retVal).resolve();
            if (resolved instanceof PsiField) {
                PsiField field = (PsiField) resolved;
                PsiClass containingClass = field.getContainingClass();
                if (containingClass != null
                        && "TRUE".equals(field.getName())
                        && "java.lang.Boolean".equals(containingClass.getQualifiedName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
package com.android.tools.lint.checks;

import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class BadHostnameVerifierDetector extends Detector implements Detector.UastScanner {
    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String VERIFY_METHOD_NAME = "verify";

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(VERIFY_METHOD_NAME);
    }

    @Nullable
    @Override
    public InspectionResult visitMethod(@NotNull UMethod method, @Nullable UClass uClass) {
        if (HOSTNAME_VERIFIER.equals(uClass.getQualifiedName())) {
            for (UReturnExpression ret : method.getReturnExpressions()) {
                if ("true".equals(ret.getValue().getText())) {
                    return InspectionResult.warning("Insecure HostnameVerifier implementation that always returns true")
                            .fix("Fix this issue by implementing a secure HostnameVerifier.")
                            .build();
                }
            }
        }
        return InspectionResult.NONE;
    }

    private static final Issue ISSUE = Issue.create(
            "BadHostnameVerifier",
            "Insecure HostnameVerifier implementation that always returns true",
            "Implementations of `HostnameVerifier` whose `verify` method always returns true could result in insecure network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            5,
            Severity.WARNING,
            new Implementation(
                    BadHostnameVerifierDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );
}
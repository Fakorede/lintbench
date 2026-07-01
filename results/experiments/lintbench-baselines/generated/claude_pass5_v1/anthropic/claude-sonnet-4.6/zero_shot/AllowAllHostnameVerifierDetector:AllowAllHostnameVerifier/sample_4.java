/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UCallExpression;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE =
            Issue.create(
                            "AllowAllHostnameVerifier",
                            "Insecure `HostnameVerifier`",
                            "This check looks for use of HostnameVerifier implementations "
                                    + "whose `verify` method always returns true (thus trusting any hostname) "
                                    + "which could result in insecure network traffic caused by trusting arbitrary "
                                    + "hostnames in TLS/SSL certificates presented by peers.",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            new Implementation(
                                    AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE))
                    .addMoreInfo("https://goo.gle/AllowAllHostnameVerifier");

    private static final String ALLOW_ALL_HOSTNAME_VERIFIER =
            "org.apache.http.conn.ssl.AllowAllHostnameVerifier";
    private static final String SSL_SOCKET_FACTORY = "org.apache.http.conn.ssl.SSLSocketFactory";
    private static final String BROWSER_COMPAT_HOST_NAME_VERIFIER =
            "org.apache.http.conn.ssl.BrowserCompatHostnameVerifier";

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("setHostnameVerifier", "setDefaultHostnameVerifier");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression call, PsiMethod method) {
        // Check for SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER field reference
        // and setHostnameVerifier / setDefaultHostnameVerifier calls
        // with ALLOW_ALL_HOSTNAME_VERIFIER or new AllowAllHostnameVerifier()

        List<org.jetbrains.uast.UExpression> args = call.getValueArguments();
        if (args.isEmpty()) {
            return;
        }

        org.jetbrains.uast.UExpression arg = args.get(0);
        String argType = null;

        com.intellij.psi.PsiType type = arg.getExpressionType();
        if (type != null) {
            argType = type.getCanonicalText();
        }

        // Check if the argument is of type AllowAllHostnameVerifier
        if (ALLOW_ALL_HOSTNAME_VERIFIER.equals(argType)) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(call),
                    "Using `AllowAllHostnameVerifier` is unsafe because it always returns "
                            + "true, which could expose the app to man-in-the-middle attacks");
            return;
        }

        // Check for SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER field reference
        if (arg instanceof org.jetbrains.uast.UReferenceExpression) {
            org.jetbrains.uast.UReferenceExpression ref =
                    (org.jetbrains.uast.UReferenceExpression) arg;
            String resolvedName = ref.getResolvedName();
            if ("ALLOW_ALL_HOSTNAME_VERIFIER".equals(resolvedName)) {
                com.intellij.psi.PsiElement resolved = ref.resolve();
                if (resolved instanceof com.intellij.psi.PsiField) {
                    com.intellij.psi.PsiField field = (com.intellij.psi.PsiField) resolved;
                    String containingClass =
                            field.getContainingClass() != null
                                    ? field.getContainingClass().getQualifiedName()
                                    : null;
                    if (SSL_SOCKET_FACTORY.equals(containingClass)) {
                        context.report(
                                ISSUE,
                                call,
                                context.getLocation(call),
                                "Using `SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER` is "
                                        + "unsafe because it always returns true, which could "
                                        + "expose the app to man-in-the-middle attacks");
                        return;
                    }
                }
            }
        }
    }
}
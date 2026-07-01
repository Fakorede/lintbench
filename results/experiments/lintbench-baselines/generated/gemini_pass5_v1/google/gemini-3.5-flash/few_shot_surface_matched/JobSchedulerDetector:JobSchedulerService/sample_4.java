package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULocalVariable;
import org.jetbrains.uast.UReferenceExpression;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                            "JobSchedulerService",
                            "JobScheduler problems",
                            "This check looks for various common mistakes in using the JobScheduler API: "
                                    + "the service class must extend `JobService`, the service must be registered in "
                                    + "the manifest and the registration must require the permission `android.permission.BIND_JOB_SERVICE`.",
                            Category.CORRECTNESS,
                            5,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public JobSchedulerDetector() {}

    @org.jetbrains.annotations.Nullable
    @Override
    public java.util.List<String> getApplicableConstructorTypes() {
        return java.util.Collections.singletonList("android.app.job.JobInfo.Builder");
    }

    @Override
    public void visitConstructor(
            @org.jetbrains.annotations.NonNull JavaContext context,
            @org.jetbrains.annotations.NonNull UCallExpression node,
            @org.jetbrains.annotations.NonNull PsiMethod constructor) {
        java.util.List<UExpression> valueArguments = node.getValueArguments();
        if (valueArguments.size() < 2) {
            return;
        }
        UExpression component = valueArguments.get(1);
        PsiClass serviceClass = findServiceClass(context, component);
        if (serviceClass == null) {
            return;
        }

        String serviceClassName = serviceClass.getQualifiedName();
        if (serviceClassName == null) {
            return;
        }

        if (!context.getEvaluator().inheritsFrom(serviceClass, "android.app.job.JobService", false)) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "The service class " + serviceClassName + " must extend android.app.job.JobService"
            );
            return;
        }

        context.getPartialResults(ISSUE).map().put(serviceClassName, context.getLocation(node));
    }

    @org.jetbrains.annotations.Nullable
    private static PsiClass findServiceClass(
            @org.jetbrains.annotations.NonNull JavaContext context,
            @org.jetbrains.annotations.NonNull UExpression expression) {
        UExpression current = expression;
        if (current instanceof UReferenceExpression) {
            PsiElement resolved = ((UReferenceExpression) current).resolve();
            if (resolved != null) {
                UElement uResolved = context.getUastContext().getUElement(resolved);
                if (uResolved instanceof ULocalVariable) {
                    ULocalVariable local = (ULocalVariable) uResolved;
                    UExpression initializer = local.getUastInitializer();
                    if (initializer != null) {
                        current = initializer;
                    }
                }
            }
        }
        if (current instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) current;
            PsiMethod method = call.resolve();
            if (method != null && method.isConstructor()) {
                PsiClass containingClass = method.getContainingClass();
                if (containingClass != null && "android.content.ComponentName".equals(containingClass.getQualifiedName())) {
                    java.util.List<UExpression> arguments = call.getValueArguments();
                    if (arguments.size() == 2) {
                        UExpression arg1 = arguments.get(1);
                        if (arg1 instanceof UClassLiteralExpression) {
                            UClassLiteralExpression literal = (UClassLiteralExpression) arg1;
                            PsiType type = literal.getType();
                            if (type != null) {
                                return context.getEvaluator().findClass(type.getCanonicalText());
                            }
                        } else {
                            Object value = arg1.evaluate();
                            if (value instanceof String) {
                                return context.getEvaluator().findClass((String) value);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void checkPartialResults(
            @org.jetbrains.annotations.NonNull Context context,
            @org.jetbrains.annotations.NonNull PartialResult partialResults) {
        org.w3c.dom.Document manifest = context.getProject().getMergedManifest();
        if (manifest == null) {
            return;
        }

        LintMap map = partialResults.map();
        for (String serviceClassName : map.keys()) {
            Location location = map.getLocation(serviceClassName);
            if (location == null) {
                continue;
            }

            org.w3c.dom.Element serviceElement = findServiceElement(manifest, serviceClassName);
            if (serviceElement == null) {
                context.report(
                        ISSUE,
                        location,
                        "Service " + serviceClassName + " must be registered in the manifest"
                );
            } else {
                String permission = serviceElement.getAttributeNS("http://schemas.android.com/apk/res/android", "permission");
                if (!"android.permission.BIND_JOB_SERVICE".equals(permission)) {
                    context.report(
                            ISSUE,
                            location,
                            "Service " + serviceClassName + " must require the permission android.permission.BIND_JOB_SERVICE"
                    );
                }
            }
        }
    }

    @org.jetbrains.annotations.Nullable
    private static org.w3c.dom.Element findServiceElement(
            @org.jetbrains.annotations.NonNull org.w3c.dom.Document manifest,
            @org.jetbrains.annotations.NonNull String serviceClassName) {
        org.w3c.dom.NodeList services = manifest.getElementsByTagName("service");
        for (int i = 0; i < services.getLength(); i++) {
            org.w3c.dom.Node node = services.item(i);
            if (node instanceof org.w3c.dom.Element) {
                org.w3c.dom.Element service = (org.w3c.dom.Element) node;
                String name = service.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                if (isMatchingName(name, serviceClassName, manifest)) {
                    return service;
                }
            }
        }
        return null;
    }

    private static boolean isMatchingName(
            @org.jetbrains.annotations.NonNull String manifestName,
            @org.jetbrains.annotations.NonNull String serviceClassName,
            @org.jetbrains.annotations.NonNull org.w3c.dom.Document manifest) {
        if (manifestName.isEmpty()) {
            return false;
        }
        if (manifestName.equals(serviceClassName)) {
            return true;
        }
        String pkg = manifest.getDocumentElement().getAttribute("package");
        if (pkg == null) {
            pkg = "";
        }
        if (manifestName.startsWith(".")) {
            return (pkg + manifestName).equals(serviceClassName);
        }
        if (!manifestName.contains(".")) {
            return (pkg + "." + manifestName).equals(serviceClassName);
        }
        return false;
    }
}
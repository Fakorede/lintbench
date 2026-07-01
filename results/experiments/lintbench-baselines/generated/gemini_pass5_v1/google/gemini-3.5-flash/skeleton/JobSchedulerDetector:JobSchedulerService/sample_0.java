package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UExpression;
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
                    IMPLEMENTATION);

    @Override
    public java.util.List<String> getApplicableConstructorTypes() {
        return java.util.Collections.singletonList("android.app.job.JobInfo.Builder");
    }

    @Override
    public void visitConstructor(
            @com.android.annotations.NonNull JavaContext context,
            @com.android.annotations.NonNull UCallExpression node,
            @com.android.annotations.NonNull PsiMethod constructor) {
        if (node.getValueArgumentCount() < 2) {
            return;
        }
        UExpression componentArg = node.getValueArgument(1);
        if (componentArg == null) {
            return;
        }

        String serviceClass = resolveServiceClass(context, componentArg);
        if (serviceClass == null) {
            return;
        }

        PsiClass psiClass = context.getEvaluator().findClass(serviceClass);
        if (psiClass != null) {
            if (!context.getEvaluator().extendsClass(psiClass, "android.app.job.JobService", false)) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Service class must extend `android.app.job.JobService`");
                return;
            }
        }

        LintMap map = context.getPartialResults(ISSUE).map(context.getProject());
        String services = map.getString("services");
        if (services == null) {
            services = "";
        }
        if (!services.isEmpty()) {
            services += "|";
        }
        int start = context.getLocation(node).getStart().getOffset();
        int end = context.getLocation(node).getEnd().getOffset();
        services += serviceClass + ";" + context.file.getPath() + ";" + start + ";" + end;
        map.put("services", services);
    }

    private String resolveServiceClass(JavaContext context, UExpression componentArg) {
        UExpression expr = componentArg;
        for (int i = 0; i < 10; i++) {
            if (expr instanceof UReferenceExpression) {
                PsiElement resolved = ((UReferenceExpression) expr).resolve();
                if (resolved instanceof PsiLocalVariable) {
                    UExpression initializer = context.getUastContext().getInitializerFor((PsiLocalVariable) resolved);
                    if (initializer != null) {
                        expr = initializer;
                        continue;
                    }
                } else if (resolved instanceof PsiField) {
                    UExpression initializer = context.getUastContext().getInitializerFor((PsiField) resolved);
                    if (initializer != null) {
                        expr = initializer;
                        continue;
                    }
                }
            }
            break;
        }

        if (expr instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) expr;
            PsiMethod resolvedMethod = call.resolve();
            if (resolvedMethod != null && resolvedMethod.getContainingClass() != null) {
                String className = resolvedMethod.getContainingClass().getQualifiedName();
                if ("android.content.ComponentName".equals(className)) {
                    java.util.List<UExpression> args = call.getValueArguments();
                    if (args.size() == 2) {
                        UExpression arg2 = args.get(1);
                        if (arg2 instanceof UClassLiteralExpression) {
                            UClassLiteralExpression classLiteral = (UClassLiteralExpression) arg2;
                            UExpression classExpr = classLiteral.getExpression();
                            if (classExpr != null) {
                                PsiType type = classExpr.getExpressionType();
                                if (type != null) {
                                    return type.getCanonicalText();
                                }
                                if (classExpr instanceof UReferenceExpression) {
                                    PsiElement resolved = ((UReferenceExpression) classExpr).resolve();
                                    if (resolved instanceof PsiClass) {
                                        return ((PsiClass) resolved).getQualifiedName();
                                    }
                                }
                            }
                        }
                        Object value = arg2.evaluate();
                        if (value instanceof String) {
                            return (String) value;
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void checkPartialResults(
            @com.android.annotations.NonNull Context context,
            @com.android.annotations.NonNull PartialResult partialResults) {
        org.w3c.dom.Document manifest = context.getProject().getMergedManifest();
        if (manifest == null) {
            return;
        }

        String ANDROID_NS = "http://schemas.android.com/apk/res/android";
        String pkg = manifest.getDocumentElement().getAttribute("package");

        java.util.Map<String, String> registeredServices = new java.util.HashMap<>();
        org.w3c.dom.NodeList serviceNodes = manifest.getElementsByTagName("service");
        for (int i = 0; i < serviceNodes.getLength(); i++) {
            org.w3c.dom.Element serviceElement = (org.w3c.dom.Element) serviceNodes.item(i);
            String name = serviceElement.getAttributeNS(ANDROID_NS, "name");
            if (name.isEmpty()) {
                name = serviceElement.getAttribute("android:name");
            }
            if (!name.isEmpty()) {
                if (name.startsWith(".")) {
                    name = pkg + name;
                } else if (!name.contains(".")) {
                    if (pkg != null && !pkg.isEmpty()) {
                        name = pkg + "." + name;
                    }
                }
                String permission = serviceElement.getAttributeNS(ANDROID_NS, "permission");
                if (permission.isEmpty()) {
                    permission = serviceElement.getAttribute("android:permission");
                }
                registeredServices.put(name.replace('$', '.'), permission);
            }
        }

        for (Project project : partialResults.getProjects()) {
            LintMap map = partialResults.map(project);
            String services = map.getString("services");
            if (services != null && !services.isEmpty()) {
                String[] entries = services.split("\\|");
                for (String entry : entries) {
                    String[] parts = entry.split(";");
                    if (parts.length < 4) {
                        continue;
                    }
                    String serviceClass = parts[0];
                    String filePath = parts[1];
                    int start = Integer.parseInt(parts[2]);
                    int end = Integer.parseInt(parts[3]);

                    java.io.File file = new java.io.File(filePath);
                    Location location = Location.create(file, start, end);

                    String normalizedServiceClass = serviceClass.replace('$', '.');

                    if (!registeredServices.containsKey(normalizedServiceClass)) {
                        context.report(
                                ISSUE,
                                location,
                                "The service `" + serviceClass + "` must be registered in the manifest");
                    } else {
                        String permission = registeredServices.get(normalizedServiceClass);
                        if (!"android.permission.BIND_JOB_SERVICE".equals(permission)) {
                            context.report(
                                    ISSUE,
                                    location,
                                    "The service `" + serviceClass + "` must require the permission `android.permission.BIND_JOB_SERVICE`");
                        }
                    }
                }
            }
        }
    }
}
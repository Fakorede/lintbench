package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResults;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                            "JobSchedulerService",
                            "JobScheduler problems",
                            "This check looks for various common mistakes in using the JobScheduler API: "
                                    + "the service class must extend `JobService`, the service must be registered "
                                    + "in the manifest and the registration must require the permission "
                                    + "`android.permission.BIND_JOB_SERVICE`.",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public JobSchedulerDetector() {}

    @Override
    @org.jetbrains.annotations.Nullable
    public java.util.List<String> getApplicableConstructorTypes() {
        return java.util.Collections.singletonList("android.app.job.JobInfo.Builder");
    }

    @Override
    public void visitConstructor(
            @org.jetbrains.annotations.NotNull JavaContext context,
            @org.jetbrains.annotations.NotNull UCallExpression node,
            @org.jetbrains.annotations.NotNull PsiMethod constructor) {
        java.util.List<UExpression> valueArguments = node.getValueArguments();
        if (valueArguments.size() < 2) {
            return;
        }
        UExpression serviceArg = valueArguments.get(1);
        String serviceClass = getServiceClassName(serviceArg, context);
        if (serviceClass == null || serviceClass.isEmpty()) {
            return;
        }

        Location location = context.getLocation(node);
        java.io.File file = context.file;
        int startOffset = location.getStart() != null ? location.getStart().getOffset() : -1;
        int endOffset = location.getEnd() != null ? location.getEnd().getOffset() : -1;

        if (startOffset != -1 && endOffset != -1) {
            String value = file.getPath() + ";" + startOffset + ";" + endOffset;
            LintMap map = context.getPartialResults(ISSUE).map();
            String existing = map.getString(serviceClass);
            if (existing != null) {
                value = existing + "|" + value;
            }
            map.put(serviceClass, value);
        }
    }

    @Override
    public void checkPartialResults(
            @org.jetbrains.annotations.NotNull Context context,
            @org.jetbrains.annotations.NotNull PartialResults partialResults) {
        LintMap map = partialResults.map();
        for (String serviceClass : map.keys()) {
            boolean extendsJobService = true;
            PsiClass psiClass = context.getEvaluator().findClass(serviceClass);
            if (psiClass != null) {
                extendsJobService = context.getEvaluator().extendsClass(psiClass, "android.app.job.JobService", false);
            }

            boolean registered = false;
            boolean hasPermission = false;

            org.w3c.dom.Document manifest = context.getMainProject().getMergedManifest();
            if (manifest != null) {
                org.w3c.dom.Element root = manifest.getDocumentElement();
                if (root != null) {
                    String pkg = root.getAttribute("package");
                    if (pkg == null || pkg.isEmpty()) {
                        pkg = context.getMainProject().getPackage();
                    }
                    org.w3c.dom.NodeList serviceList = root.getElementsByTagName("service");
                    for (int i = 0; i < serviceList.getLength(); i++) {
                        org.w3c.dom.Element serviceElement = (org.w3c.dom.Element) serviceList.item(i);
                        String name = serviceElement.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                        if (name == null || name.isEmpty()) {
                            name = serviceElement.getAttribute("android:name");
                        }
                        if (name != null && !name.isEmpty()) {
                            if (name.startsWith(".")) {
                                name = pkg + name;
                            } else if (!name.contains(".")) {
                                name = pkg + "." + name;
                            }
                            String normalizedServiceClass = serviceClass.replace('$', '.');
                            String normalizedManifestName = name.replace('$', '.');
                            if (normalizedServiceClass.equals(normalizedManifestName)) {
                                registered = true;
                                String permission = serviceElement.getAttributeNS("http://schemas.android.com/apk/res/android", "permission");
                                if (permission == null || permission.isEmpty()) {
                                    permission = serviceElement.getAttribute("android:permission");
                                }
                                if ("android.permission.BIND_JOB_SERVICE".equals(permission)) {
                                    hasPermission = true;
                                }
                                break;
                            }
                        }
                    }
                }
            }

            String locationString = map.getString(serviceClass);
            if (locationString != null) {
                String[] parts = locationString.split("\\|");
                for (String part : parts) {
                    if (part.isEmpty()) continue;
                    String[] coords = part.split(";");
                    if (coords.length == 3) {
                        try {
                            String filePath = coords[0];
                            int startOffset = Integer.parseInt(coords[1]);
                            int endOffset = Integer.parseInt(coords[2]);
                            java.io.File file = new java.io.File(filePath);
                            CharSequence contents = context.getClient().getCharSequence(file);
                            Location location = Location.create(file, contents, startOffset, endOffset);

                            if (!extendsJobService) {
                                context.report(
                                        ISSUE,
                                        location,
                                        "The service class `" + serviceClass + "` must extend `android.app.job.JobService`");
                            }
                            if (!registered) {
                                context.report(
                                        ISSUE,
                                        location,
                                        "The service `" + serviceClass + "` must be registered in the manifest");
                            } else if (!hasPermission) {
                                context.report(
                                        ISSUE,
                                        location,
                                        "The service `" + serviceClass + "` registration must require the permission `android.permission.BIND_JOB_SERVICE`");
                            }
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }
                }
            }
        }
    }

    private static String getServiceClassName(UExpression expression, JavaContext context) {
        if (expression instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) expression;
            PsiMethod constructor = call.resolve();
            if (constructor != null && constructor.isConstructor()) {
                PsiClass containingClass = constructor.getContainingClass();
                if (containingClass != null && "android.content.ComponentName".equals(containingClass.getQualifiedName())) {
                    java.util.List<UExpression> args = call.getValueArguments();
                    if (args.size() == 2) {
                        UExpression secondArg = args.get(1);
                        return getClassNameFromExpression(secondArg, context);
                    }
                }
            }
        }
        return null;
    }

    private static String getClassNameFromExpression(UExpression expression, JavaContext context) {
        if (expression instanceof UClassLiteralExpression) {
            UClassLiteralExpression classLiteral = (UClassLiteralExpression) expression;
            PsiType type = classLiteral.getType();
            if (type instanceof PsiClassType) {
                PsiClassType classType = (PsiClassType) type;
                PsiType[] parameters = classType.getParameters();
                if (parameters.length > 0) {
                    PsiType paramType = parameters[0];
                    if (paramType instanceof PsiClassType) {
                        PsiClass resolvedClass = ((PsiClassType) paramType).resolve();
                        if (resolvedClass != null) {
                            return resolvedClass.getQualifiedName();
                        }
                    }
                    return paramType.getCanonicalText();
                }
            }
            UExpression classExpression = classLiteral.getExpression();
            if (classExpression != null) {
                PsiType classType = classExpression.getExpressionType();
                if (classType instanceof PsiClassType) {
                    PsiClass resolvedClass = ((PsiClassType) classType).resolve();
                    if (resolvedClass != null) {
                        return resolvedClass.getQualifiedName();
                    }
                }
            }
        } else if (expression instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expression).getValue();
            if (value instanceof String) {
                return (String) value;
            }
        } else {
            PsiType type = expression.getExpressionType();
            if (type instanceof PsiClassType) {
                PsiClassType classType = (PsiClassType) type;
                PsiType[] parameters = classType.getParameters();
                if (parameters.length > 0) {
                    PsiType paramType = parameters[0];
                    if (paramType instanceof PsiClassType) {
                        PsiClass resolvedClass = ((PsiClassType) paramType).resolve();
                        if (resolvedClass != null) {
                            return resolvedClass.getQualifiedName();
                        }
                    }
                    return paramType.getCanonicalText();
                }
            }
        }
        return null;
    }
}
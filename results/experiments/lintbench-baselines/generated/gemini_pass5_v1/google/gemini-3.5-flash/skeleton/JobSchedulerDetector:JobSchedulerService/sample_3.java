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
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UExpression;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "JobSchedulerService",
                    "JobScheduler problems",
                    "This check looks for various common mistakes in using the JobScheduler API: "
                            + "the service class must extend `JobService`, the service must be registered in "
                            + "the manifest and the registration must require the permission "
                            + "`android.permission.BIND_JOB_SERVICE`.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Arrays.asList("android.app.job.JobInfo.Builder", "android.app.job.JobInfo$Builder");
    }

    @Override
    public void visitConstructor(
            JavaContext context,
            UCallExpression node,
            PsiMethod constructor) {
        List<UExpression> arguments = node.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }

        UExpression componentNameArg = arguments.get(1);
        String className = null;
        PsiClass serviceClass = null;

        if (componentNameArg instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) componentNameArg;
            PsiMethod resolvedMethod = call.resolve();
            if (resolvedMethod != null && resolvedMethod.isConstructor()) {
                PsiClass containingClass = resolvedMethod.getContainingClass();
                if (containingClass != null && "android.content.ComponentName".equals(containingClass.getQualifiedName())) {
                    List<UExpression> compArgs = call.getValueArguments();
                    if (compArgs.size() == 2) {
                        UExpression classArg = compArgs.get(1);
                        if (classArg instanceof UClassLiteralExpression) {
                            UClassLiteralExpression classLiteral = (UClassLiteralExpression) classArg;
                            PsiType type = classLiteral.getType();
                            if (type instanceof PsiClassType) {
                                PsiClassType classType = (PsiClassType) type;
                                PsiClass resolved = classType.resolve();
                                if (resolved != null && "java.lang.Class".equals(resolved.getQualifiedName())) {
                                    if (classType.getParameterCount() == 1) {
                                        PsiType paramType = classType.getParameters()[0];
                                        if (paramType instanceof PsiClassType) {
                                            serviceClass = ((PsiClassType) paramType).resolve();
                                        }
                                    }
                                } else {
                                    serviceClass = resolved;
                                }
                            }
                            if (serviceClass != null) {
                                className = serviceClass.getQualifiedName();
                            }
                        } else {
                            Object evaluated = classArg.evaluate();
                            if (evaluated instanceof String) {
                                className = (String) evaluated;
                                serviceClass = context.getEvaluator().findClass(className);
                            }
                        }
                    }
                }
            }
        }

        if (className != null) {
            if (serviceClass != null) {
                boolean extendsJobService = context.getEvaluator().extendsClass(serviceClass, "android.app.job.JobService", true);
                if (!extendsJobService) {
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "The service class must extend `android.app.job.JobService`"
                    );
                }
            }

            PartialResult partialResults = context.getPartialResults(ISSUE);
            LintMap map = partialResults.map();
            int count = map.getInt("count", 0);
            map.put("class_" + count, className);
            map.put("location_" + count, context.getLocation(node));
            map.put("count", count + 1);
        }
    }

    @Override
    public void checkPartialResults(
            Context context, PartialResult partialResults) {
        LintMap map = partialResults.map();
        int count = map.getInt("count", 0);
        if (count == 0) {
            return;
        }

        Document mergedManifest = context.getProject().getMergedManifest();
        if (mergedManifest == null) {
            mergedManifest = context.getMainProject().getMergedManifest();
        }
        if (mergedManifest == null) {
            return;
        }

        NodeList services = mergedManifest.getElementsByTagName("service");
        java.util.Map<String, Element> registeredServices = new java.util.HashMap<>();
        String packageName = mergedManifest.getDocumentElement().getAttribute("package");

        for (int i = 0; i < services.getLength(); i++) {
            Node node = services.item(i);
            if (node instanceof Element) {
                Element serviceElement = (Element) node;
                String name = serviceElement.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                if (name.isEmpty()) {
                    name = serviceElement.getAttribute("android:name");
                }
                if (!name.isEmpty()) {
                    String fullName = name;
                    if (name.startsWith(".")) {
                        fullName = packageName + name;
                    } else if (!name.contains(".")) {
                        fullName = packageName + "." + name;
                    }
                    registeredServices.put(fullName, serviceElement);
                }
            }
        }

        for (int i = 0; i < count; i++) {
            String className = map.getString("class_" + i);
            Location location = map.getLocation("location_" + i);
            if (className == null || location == null) {
                continue;
            }

            if (!registeredServices.containsKey(className)) {
                context.report(
                        ISSUE,
                        location,
                        "The service `" + className + "` must be registered in the manifest"
                );
            } else {
                Element serviceElement = registeredServices.get(className);
                String permission = serviceElement.getAttributeNS("http://schemas.android.com/apk/res/android", "permission");
                if (permission.isEmpty()) {
                    permission = serviceElement.getAttribute("android:permission");
                }
                if (!"android.permission.BIND_JOB_SERVICE".equals(permission)) {
                    context.report(
                            ISSUE,
                            location,
                            "The service `" + className + "` must require the permission `android.permission.BIND_JOB_SERVICE`"
                    );
                }
            }
        }
    }
}
package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UExpression;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                            "JobSchedulerService",
                            "JobScheduler problems",
                            "This check looks for various common mistakes in using the JobScheduler API: "
                                    + "the service class must extend `JobService`, the service must be registered in the manifest "
                                    + "and the registration must require the permission `android.permission.BIND_JOB_SERVICE`.",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public JobSchedulerDetector() {}

    @Nullable
    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList("android.app.job.JobInfo.Builder");
    }

    @Override
    public void visitConstructor(@NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod constructor) {
        List<UExpression> args = node.getValueArguments();
        if (args.size() < 2) {
            return;
        }
        UExpression componentArg = args.get(1);
        PsiClass serviceClass = findJobServiceClass(componentArg);
        if (serviceClass == null) {
            return;
        }

        boolean extendsJobService = false;
        PsiClass current = serviceClass;
        while (current != null) {
            if ("android.app.job.JobService".equals(current.getQualifiedName())) {
                extendsJobService = true;
                break;
            }
            current = current.getSuperClass();
        }

        if (!extendsJobService) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Service class must extend `android.app.job.JobService`"
            );
            return;
        }

        org.w3c.dom.Document document = context.getProject().getMergedManifest();
        if (document != null) {
            String targetService = serviceClass.getQualifiedName();
            if (targetService == null) return;

            org.w3c.dom.Element root = document.getDocumentElement();
            String pkg = root != null ? root.getAttribute("package") : "";
            org.w3c.dom.NodeList services = document.getElementsByTagName("service");
            boolean found = false;
            boolean hasPermission = false;

            for (int i = 0; i < services.getLength(); i++) {
                org.w3c.dom.Element serviceElement = (org.w3c.dom.Element) services.item(i);
                String name = serviceElement.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                if (name == null || name.isEmpty()) {
                    continue;
                }
                String fqName = name;
                if (name.startsWith(".")) {
                    fqName = pkg + name;
                } else if (!name.contains(".")) {
                    fqName = pkg + "." + name;
                }

                if (targetService.equals(fqName)) {
                    found = true;
                    String permission = serviceElement.getAttributeNS("http://schemas.android.com/apk/res/android", "permission");
                    if ("android.permission.BIND_JOB_SERVICE".equals(permission)) {
                        hasPermission = true;
                    }
                    break;
                }
            }

            if (!found) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        String.format("The service `%s` must be registered in the manifest", targetService)
                );
            } else if (!hasPermission) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        String.format("The service `%s` must require `android.permission.BIND_JOB_SERVICE` permission", targetService)
                );
            }
        }
    }

    @Override
    public void checkPartialResults(@NonNull Context context, @NonNull PartialResult partialResult) {
        // Overridden per specification requirements
    }

    @Nullable
    private PsiClass findJobServiceClass(@NonNull UExpression expression) {
        if (expression instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) expression;
            PsiMethod constructor = call.resolve();
            if (constructor != null) {
                PsiClass containingClass = constructor.getContainingClass();
                if (containingClass != null && "android.content.ComponentName".equals(containingClass.getQualifiedName())) {
                    List<UExpression> args = call.getValueArguments();
                    if (args.size() == 2) {
                        UExpression secondArg = args.get(1);
                        if (secondArg instanceof UClassLiteralExpression) {
                            UClassLiteralExpression classLiteral = (UClassLiteralExpression) secondArg;
                            PsiType type = classLiteral.getType();
                            if (type instanceof PsiClassType) {
                                return ((PsiClassType) type).resolve();
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
}
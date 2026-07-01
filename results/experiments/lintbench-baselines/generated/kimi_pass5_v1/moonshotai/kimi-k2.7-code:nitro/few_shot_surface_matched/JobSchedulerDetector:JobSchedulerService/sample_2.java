package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UastLiteralUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String JOB_SERVICE = "android.app.job.JobService";
    private static final String BIND_JOB_SERVICE = "android.permission.BIND_JOB_SERVICE";
    private static final String COMPONENT_NAME = "android.content.ComponentName";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE =
            Issue.create(
                            "JobSchedulerService",
                            "JobScheduler problems",
                            "This check looks for various common mistakes in using the "
                                    + "JobScheduler API: the service class must extend `JobService`, "
                                    + "the service must be registered in the manifest and the "
                                    + "registration must require the permission "
                                    + "`android.permission.BIND_JOB_SERVICE`.",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public JobSchedulerDetector() {}

    @Nullable
    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList(COMPONENT_NAME);
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod constructor) {
        List<UExpression> args = node.getValueArguments();
        if (args.size() != 2) {
            return;
        }

        UExpression argument = args.get(1);
        String className = getServiceClassName(argument);
        if (className == null) {
            return;
        }

        PsiClass cls = context.getEvaluator().findClass(className);
        if (cls != null && !context.getEvaluator().extendsClass(cls, JOB_SERVICE, false)) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "The service class `" + className + "` must extend `android.app.job.JobService`");
            return;
        }

        context.getPartialResults(ISSUE).getMap().put(className, className);
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResult) {
        Map<String, Object> map = partialResult.getMap();
        if (map.isEmpty()) {
            return;
        }

        Document manifest = context.getMainProject().getMergedManifest();
        if (manifest == null) {
            return;
        }
        Element root = manifest.getDocumentElement();
        if (root == null) {
            return;
        }

        String packageName = context.getMainProject().getPackage();
        NodeList services = root.getElementsByTagName("service");
        Location reportLocation = createReportLocation(context);

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String className = entry.getKey();
            String expandedClass = expandClassName(className, packageName);

            boolean registered = false;
            for (int i = 0; i < services.getLength(); i++) {
                Element service = (Element) services.item(i);
                String nameAttr = service.getAttributeNS(ANDROID_URI, "name");
                if (nameAttr == null || nameAttr.isEmpty()) {
                    continue;
                }

                String expandedAttr = expandClassName(nameAttr, packageName);
                if (expandedClass.equals(expandedAttr)) {
                    registered = true;
                    String permission = service.getAttributeNS(ANDROID_URI, "permission");
                    if (!BIND_JOB_SERVICE.equals(permission)) {
                        context.report(
                                ISSUE,
                                reportLocation,
                                "The service `"
                                        + className
                                        + "` must require the permission `"
                                        + BIND_JOB_SERVICE
                                        + "`");
                    }
                    break;
                }
            }

            if (!registered) {
                context.report(
                        ISSUE,
                        reportLocation,
                        "The service `" + className + "` must be registered in the manifest");
            }
        }
    }

    @Nullable
    private static String getServiceClassName(@NonNull UExpression argument) {
        if (argument instanceof UClassLiteralExpression) {
            PsiType type = ((UClassLiteralExpression) argument).getType();
            if (type instanceof PsiClassType) {
                PsiClass resolved = ((PsiClassType) type).resolve();
                if (resolved != null) {
                    return resolved.getQualifiedName();
                }
            }
        }

        return UastLiteralUtils.getStringValue(argument);
    }

    @NonNull
    private static String expandClassName(@NonNull String name, @Nullable String packageName) {
        if (name.startsWith(".")) {
            return (packageName != null ? packageName : "") + name;
        }
        if (!name.contains(".")) {
            return (packageName != null && !packageName.isEmpty() ? packageName + "." : "") + name;
        }
        return name;
    }

    @NonNull
    private static Location createReportLocation(@NonNull Context context) {
        List<File> manifests = context.getMainProject().getManifestFiles();
        if (!manifests.isEmpty()) {
            return Location.create(manifests.get(0));
        }
        return Location.create(context.getFile());
    }
}
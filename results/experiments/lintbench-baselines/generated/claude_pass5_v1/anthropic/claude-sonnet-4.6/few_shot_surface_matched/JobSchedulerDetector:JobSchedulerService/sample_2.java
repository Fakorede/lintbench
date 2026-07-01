package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final String JOB_SCHEDULER_CLASS = "android.app.job.JobScheduler";
    private static final String JOB_INFO_BUILDER_CLASS = "android.app.job.JobInfo.Builder";
    private static final String JOB_SERVICE_CLASS = "android.app.job.JobService";
    private static final String BIND_JOB_SERVICE_PERMISSION = "android.permission.BIND_JOB_SERVICE";

    private static final String KEY_SERVICE_CLASS = "serviceClass";
    private static final String KEY_LOCATION = "location";
    private static final String KEY_MESSAGE = "message";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    JobSchedulerDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST));

    public static final Issue ISSUE =
            Issue.create(
                            "JobSchedulerService",
                            "JobScheduler problems",
                            "This check looks for various common mistakes in using the "
                                    + " JobScheduler API: the service class must extend `JobService`, "
                                    + " the service must be registered in the manifest and the registration "
                                    + " must require the permission `android.permission.BIND_JOB_SERVICE`.",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/topic/performance/scheduling.html")
                    .setAndroidSpecific(true);

    public JobSchedulerDetector() {}

    @Nullable
    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList(JOB_INFO_BUILDER_CLASS);
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod constructor) {
        List<UExpression> args = call.getValueArguments();
        if (args.size() < 2) {
            return;
        }

        UExpression serviceComponentArg = args.get(1);
        PsiClass serviceClass = resolveServiceClass(context, serviceComponentArg);

        if (serviceClass == null) {
            return;
        }

        String qualifiedName = serviceClass.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        // Check that the service class extends JobService
        if (!context.getEvaluator().extendsClass(serviceClass, JOB_SERVICE_CLASS, false)) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(serviceComponentArg),
                    "Scheduled job class `"
                            + serviceClass.getName()
                            + "` must extend `android.app.job.JobService`");
            return;
        }

        // Store partial result for manifest check
        if (context.isGlobalAnalysis()) {
            // In global analysis, we report directly (manifest check happens elsewhere)
            LintMap map = context.getPartialResults(ISSUE).map();
            map.put(KEY_SERVICE_CLASS + "_" + qualifiedName, qualifiedName);
        } else {
            LintMap map = context.getPartialResults(ISSUE).map();
            map.put(KEY_SERVICE_CLASS + "_" + qualifiedName, qualifiedName);
        }
    }

    @Nullable
    private PsiClass resolveServiceClass(
            @NonNull JavaContext context, @NonNull UExpression expression) {
        // Try to resolve the ComponentName argument to get the service class
        // The ComponentName constructor typically takes (Context, Class<?>) or (String, String)
        // We look for class literals passed as second arg to JobInfo.Builder
        // The expression here is the second arg to JobInfo.Builder(int, ComponentName)
        // which is a ComponentName. We need to inspect the ComponentName construction.

        // Walk up to find the ComponentName construction
        // The argument is a ComponentName expression; try to resolve class from it
        if (expression instanceof UCallExpression) {
            UCallExpression callExpr = (UCallExpression) expression;
            List<UExpression> componentArgs = callExpr.getValueArguments();
            if (componentArgs.size() >= 2) {
                UExpression classArg = componentArgs.get(1);
                // Try class literal resolution
                PsiClass resolved = resolveClassFromExpression(context, classArg);
                if (resolved != null) {
                    return resolved;
                }
            }
        }

        // Try direct class literal
        return resolveClassFromExpression(context, expression);
    }

    @Nullable
    private PsiClass resolveClassFromExpression(
            @NonNull JavaContext context, @NonNull UExpression expression) {
        // Handle class literals like MyService.class
        if (expression instanceof org.jetbrains.uast.UClassLiteralExpression) {
            org.jetbrains.uast.UClassLiteralExpression classLiteral =
                    (org.jetbrains.uast.UClassLiteralExpression) expression;
            com.intellij.psi.PsiType type = classLiteral.getType();
            if (type instanceof com.intellij.psi.PsiClassType) {
                return ((com.intellij.psi.PsiClassType) type).resolve();
            }
        }
        return null;
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        // Collect all service class names that were scheduled
        LintMap combinedMap = partialResults.map();

        // Parse manifest to check service registrations
        Document manifest = getManifestDocument(context);
        if (manifest == null) {
            return;
        }

        for (String key : combinedMap) {
            if (!key.startsWith(KEY_SERVICE_CLASS + "_")) {
                continue;
            }
            String serviceClassName = combinedMap.getString(key, null);
            if (serviceClassName == null) {
                continue;
            }

            // Check manifest for this service
            checkServiceInManifest(context, manifest, serviceClassName);
        }
    }

    @Nullable
    private Document getManifestDocument(@NonNull Context context) {
        try {
            java.io.File manifestFile = context.getMainProject().getManifestFiles().stream()
                    .findFirst()
                    .orElse(null);
            if (manifestFile == null || !manifestFile.exists()) {
                return null;
            }
            javax.xml.parsers.DocumentBuilderFactory factory =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance();
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(manifestFile);
        } catch (Exception e) {
            return null;
        }
    }

    private void checkServiceInManifest(
            @NonNull Context context,
            @NonNull Document manifest,
            @NonNull String serviceClassName) {
        NodeList services = manifest.getElementsByTagName("service");
        String simpleClassName = serviceClassName.replace('.', '/');

        boolean found = false;
        boolean hasPermission = false;

        for (int i = 0; i < services.getLength(); i++) {
            Element service = (Element) services.item(i);
            String name = service.getAttributeNS(
                    "http://schemas.android.com/apk/res/android", "name");
            if (name == null || name.isEmpty()) {
                continue;
            }
            // Normalize name for comparison
            String normalizedName = name.replace('.', '/');
            String normalizedClass = serviceClassName.replace('.', '/');

            if (normalizedName.equals(normalizedClass)
                    || normalizedClass.endsWith(normalizedName)
                    || name.equals(serviceClassName)
                    || name.endsWith("." + getSimpleName(serviceClassName))) {
                found = true;
                String permission = service.getAttributeNS(
                        "http://schemas.android.com/apk/res/android", "permission");
                if (BIND_JOB_SERVICE_PERMISSION.equals(permission)) {
                    hasPermission = true;
                }
                break;
            }
        }

        if (!found) {
            context.report(
                    ISSUE,
                    com.android.tools.lint.detector.api.Location.create(
                            context.getMainProject().getManifestFiles().stream()
                                    .findFirst()
                                    .orElse(context.file)),
                    "Service `"
                            + serviceClassName
                            + "` is not registered in the manifest");
        } else if (!hasPermission) {
            context.report(
                    ISSUE,
                    com.android.tools.lint.detector.api.Location.create(
                            context.getMainProject().getManifestFiles().stream()
                                    .findFirst()
                                    .orElse(context.file)),
                    "Service `"
                            + serviceClassName
                            + "` must require the `"
                            + BIND_JOB_SERVICE_PERMISSION
                            + "` permission");
        }
    }

    @NonNull
    private String getSimpleName(@NonNull String qualifiedName) {
        int dot = qualifiedName.lastIndexOf('.');
        if (dot >= 0) {
            return qualifiedName.substring(dot + 1);
        }
        return qualifiedName;
    }
}
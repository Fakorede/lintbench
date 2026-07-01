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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final String JOB_SCHEDULER = "android.app.JobScheduler";
    private static final String JOB_INFO_BUILDER = "android.app.job.JobInfo.Builder";
    private static final String JOB_SERVICE = "android.app.job.JobService";
    private static final String BIND_JOB_SERVICE_PERMISSION = "android.permission.BIND_JOB_SERVICE";
    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";

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
                                    + "JobScheduler API: the service class must extend `JobService`, "
                                    + "the service must be registered in the manifest and the registration "
                                    + "must require the permission `android.permission.BIND_JOB_SERVICE`.",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .addMoreInfo("https://developer.android.com/topic/performance/scheduling.html")
                    .setAndroidSpecific(true);

    public JobSchedulerDetector() {}

    @Nullable
    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList(JOB_INFO_BUILDER);
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod constructor) {
        List<UExpression> arguments = call.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }

        UExpression serviceComponentArg = arguments.get(1);
        String serviceClassName = resolveServiceClassName(context, serviceComponentArg);
        if (serviceClassName == null) {
            return;
        }

        // Check if the service class extends JobService
        PsiClass serviceClass = resolveClass(context, serviceClassName);
        if (serviceClass != null && !extendsJobService(context, serviceClass)) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(serviceComponentArg),
                    "Scheduled job class `"
                            + serviceClassName
                            + "` must extend `android.app.job.JobService`");
        }

        // Store for cross-file manifest check
        LintMap map = context.getPartialResults(ISSUE).map();
        String key = "service_" + serviceClassName.replace('.', '_');
        if (!map.containsKey(key)) {
            map.put(key, serviceClassName);
        }
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        LintMap map = partialResults.map();
        if (map == null) {
            return;
        }

        // Parse the manifest to find registered services and their permissions
        Document manifest = parseManifest(context);

        for (String key : map) {
            if (!key.startsWith("service_")) {
                continue;
            }
            String serviceClassName = map.getString(key, null);
            if (serviceClassName == null) {
                continue;
            }

            if (manifest == null) {
                context.report(
                        ISSUE,
                        context.getProject().getDir(),
                        null,
                        "Service `"
                                + serviceClassName
                                + "` used with JobScheduler is not registered in the manifest");
                continue;
            }

            Element serviceElement = findServiceElement(manifest, serviceClassName);
            if (serviceElement == null) {
                context.report(
                        ISSUE,
                        context.getProject().getDir(),
                        null,
                        "Service `"
                                + serviceClassName
                                + "` used with JobScheduler is not registered in the manifest");
            } else {
                String permission = serviceElement.getAttributeNS(
                        "http://schemas.android.com/apk/res/android", "permission");
                if (!BIND_JOB_SERVICE_PERMISSION.equals(permission)) {
                    context.report(
                            ISSUE,
                            context.getProject().getDir(),
                            null,
                            "The manifest registration for `"
                                    + serviceClassName
                                    + "` does not require the permission `"
                                    + BIND_JOB_SERVICE_PERMISSION
                                    + "`");
                }
            }
        }
    }

    @Nullable
    private String resolveServiceClassName(
            @NonNull JavaContext context, @NonNull UExpression expression) {
        // Try to resolve the class literal or ComponentName argument
        // The second argument to JobInfo.Builder is a ComponentName
        // We look for class references in the expression
        String text = expression.asSourceString();
        if (text == null) {
            return null;
        }

        // Try to evaluate as a constant or class reference
        Object evaluated = expression.evaluate();
        if (evaluated instanceof String) {
            return (String) evaluated;
        }

        // Try to find class name from ComponentName construction pattern
        // ComponentName(context, MyJobService.class) or ComponentName(context, MyJobService.class.getName())
        if (expression instanceof UCallExpression) {
            UCallExpression callExpr = (UCallExpression) expression;
            List<UExpression> args = callExpr.getValueArguments();
            if (args.size() >= 2) {
                UExpression classArg = args.get(1);
                String classText = classArg.asSourceString();
                if (classText != null && classText.endsWith(".class")) {
                    String className = classText.substring(0, classText.length() - ".class".length()).trim();
                    // Try to resolve the fully qualified name
                    PsiClass resolved = resolveClass(context, className);
                    if (resolved != null && resolved.getQualifiedName() != null) {
                        return resolved.getQualifiedName();
                    }
                    return className;
                }
            }
        }

        return null;
    }

    @Nullable
    private PsiClass resolveClass(@NonNull JavaContext context, @NonNull String className) {
        return context.getEvaluator().findClass(className);
    }

    private boolean extendsJobService(@NonNull JavaContext context, @NonNull PsiClass psiClass) {
        return context.getEvaluator().extendsClass(psiClass, JOB_SERVICE, false);
    }

    @Nullable
    private Document parseManifest(@NonNull Context context) {
        try {
            java.io.File manifestFile = new java.io.File(
                    context.getProject().getDir(), ANDROID_MANIFEST_XML);
            if (!manifestFile.exists()) {
                return null;
            }
            javax.xml.parsers.DocumentBuilderFactory factory =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(manifestFile);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private Element findServiceElement(@NonNull Document manifest, @NonNull String serviceClassName) {
        NodeList services = manifest.getElementsByTagName("service");
        for (int i = 0; i < services.getLength(); i++) {
            Element service = (Element) services.item(i);
            String name = service.getAttributeNS(
                    "http://schemas.android.com/apk/res/android", "name");
            if (name == null) {
                continue;
            }
            // Handle both fully qualified and short names
            if (serviceClassName.equals(name)
                    || serviceClassName.endsWith("." + name)
                    || name.endsWith("." + getSimpleName(serviceClassName))) {
                return service;
            }
            // Handle shorthand (e.g., ".MyJobService")
            if (name.startsWith(".")) {
                String pkg = getPackageName(manifest);
                if (pkg != null && (pkg + name).equals(serviceClassName)) {
                    return service;
                }
            }
        }
        return null;
    }

    @Nullable
    private String getPackageName(@NonNull Document manifest) {
        Element root = manifest.getDocumentElement();
        if (root == null) {
            return null;
        }
        String pkg = root.getAttribute("package");
        return pkg.isEmpty() ? null : pkg;
    }

    @NonNull
    private String getSimpleName(@NonNull String qualifiedName) {
        int dot = qualifiedName.lastIndexOf('.');
        return dot >= 0 ? qualifiedName.substring(dot + 1) : qualifiedName;
    }
}
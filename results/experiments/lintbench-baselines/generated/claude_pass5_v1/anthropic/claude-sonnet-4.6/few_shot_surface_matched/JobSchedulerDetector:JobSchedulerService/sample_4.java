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

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String TAG_SERVICE = "service";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_PERMISSION = "permission";

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
                            7,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .addMoreInfo("https://developer.android.com/topic/performance/scheduling.html")
                    .setAndroidSpecific(true);

    public JobSchedulerDetector() {}

    // -------------------------------------------------------------------------
    // SourceCodeScanner
    // -------------------------------------------------------------------------

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

        // JobInfo.Builder(int jobId, ComponentName componentName)
        // The second argument is the ComponentName whose class name we want to inspect.
        List<UExpression> args = call.getValueArguments();
        if (args.size() < 2) {
            return;
        }

        UExpression componentNameArg = args.get(1);

        // Try to resolve the class literal / ComponentName constructor to find the service class.
        String serviceClassName = resolveServiceClassName(context, componentNameArg);
        if (serviceClassName == null) {
            return;
        }

        // Check 1: The referenced class must extend JobService.
        PsiClass serviceClass = context.getEvaluator().findClass(serviceClassName);
        if (serviceClass != null
                && !context.getEvaluator().extendsClass(serviceClass, JOB_SERVICE_CLASS, false)) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(componentNameArg),
                    "Scheduled job class `"
                            + serviceClassName
                            + "` must extend `android.app.job.JobService`");
            // Even if the class is wrong, still record for manifest checks.
        }

        // Store partial result so we can cross-check the manifest later.
        if (context.isGlobalAnalysis()) {
            // In single-module / global analysis we can check the manifest directly.
            checkManifestForService(context, call, serviceClassName);
        } else {
            LintMap map = context.getPartialResults(ISSUE).map();
            // Use a unique key per call site.
            String locationKey = KEY_LOCATION + "_" + serviceClassName;
            if (!map.containsKey(locationKey)) {
                map.put(locationKey, context.getLocation(call));
                map.put(KEY_SERVICE_CLASS + "_" + serviceClassName, serviceClassName);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Partial results (multi-module / global analysis)
    // -------------------------------------------------------------------------

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        LintMap map = partialResults.map();
        if (map == null) {
            return;
        }

        // Iterate over all recorded service class names.
        for (String key : map) {
            if (!key.startsWith(KEY_SERVICE_CLASS + "_")) {
                continue;
            }
            String serviceClassName = map.getString(key, null);
            if (serviceClassName == null) {
                continue;
            }
            String locationKey = KEY_LOCATION + "_" + serviceClassName;
            com.android.tools.lint.detector.api.Location location =
                    map.getLocation(locationKey, null);
            if (location == null) {
                continue;
            }
            checkManifestForServiceInContext(context, location, serviceClassName);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @Nullable
    private static String resolveServiceClassName(
            @NonNull JavaContext context, @NonNull UExpression componentNameArg) {
        // The ComponentName constructor is typically:
        //   new ComponentName(context, MyService.class)
        // or
        //   new ComponentName(context, "com.example.MyService")
        //
        // Walk the argument expression to find a class reference or string literal.

        // Try evaluating as a constant string first.
        Object value = componentNameArg.evaluate();
        if (value instanceof String) {
            return (String) value;
        }

        // Try to find a class literal inside a ComponentName constructor call.
        if (componentNameArg instanceof UCallExpression) {
            UCallExpression innerCall = (UCallExpression) componentNameArg;
            List<UExpression> innerArgs = innerCall.getValueArguments();
            for (UExpression innerArg : innerArgs) {
                // Class literal: MyService.class
                if (innerArg instanceof org.jetbrains.uast.UClassLiteralExpression) {
                    org.jetbrains.uast.UClassLiteralExpression classLiteral =
                            (org.jetbrains.uast.UClassLiteralExpression) innerArg;
                    com.intellij.psi.PsiType type = classLiteral.getType();
                    if (type != null) {
                        return type.getCanonicalText();
                    }
                }
                // String constant
                Object innerValue = innerArg.evaluate();
                if (innerValue instanceof String) {
                    return (String) innerValue;
                }
            }
        }

        return null;
    }

    private void checkManifestForService(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull String serviceClassName) {
        // In global analysis we can query the merged manifest via the project.
        // We look for a <service> element whose android:name matches serviceClassName.
        Document manifest = context.getMainProject().getMergedManifest();
        if (manifest == null) {
            return;
        }

        String simpleClassName = serviceClassName.replace('$', '.');
        NodeList services = manifest.getElementsByTagName(TAG_SERVICE);
        for (int i = 0; i < services.getLength(); i++) {
            Element service = (Element) services.item(i);
            String name = service.getAttributeNS(ANDROID_NS, ATTR_NAME);
            if (name == null) {
                continue;
            }
            // Normalize: strip leading dot, expand short names, etc.
            if (name.equals(simpleClassName)
                    || name.equals("." + simpleClassName)
                    || simpleClassName.endsWith(name)) {
                // Found a matching service registration; check the permission.
                String permission = service.getAttributeNS(ANDROID_NS, ATTR_PERMISSION);
                if (!BIND_JOB_SERVICE_PERMISSION.equals(permission)) {
                    context.report(
                            ISSUE,
                            call,
                            context.getLocation(call),
                            "The manifest registration for `"
                                    + serviceClassName
                                    + "` does not require the permission `"
                                    + BIND_JOB_SERVICE_PERMISSION
                                    + "`");
                }
                return;
            }
        }

        // No matching <service> entry found.
        context.report(
                ISSUE,
                call,
                context.getLocation(call),
                "`"
                        + serviceClassName
                        + "` is not registered in the manifest");
    }

    private void checkManifestForServiceInContext(
            @NonNull Context context,
            @NonNull com.android.tools.lint.detector.api.Location location,
            @NonNull String serviceClassName) {
        Document manifest = context.getMainProject().getMergedManifest();
        if (manifest == null) {
            return;
        }

        String simpleClassName = serviceClassName.replace('$', '.');
        NodeList services = manifest.getElementsByTagName(TAG_SERVICE);
        for (int i = 0; i < services.getLength(); i++) {
            Element service = (Element) services.item(i);
            String name = service.getAttributeNS(ANDROID_NS, ATTR_NAME);
            if (name == null) {
                continue;
            }
            if (name.equals(simpleClassName)
                    || name.equals("." + simpleClassName)
                    || simpleClassName.endsWith(name)) {
                String permission = service.getAttributeNS(ANDROID_NS, ATTR_PERMISSION);
                if (!BIND_JOB_SERVICE_PERMISSION.equals(permission)) {
                    context.report(
                            ISSUE,
                            location,
                            "The manifest registration for `"
                                    + serviceClassName
                                    + "` does not require the permission `"
                                    + BIND_JOB_SERVICE_PERMISSION
                                    + "`");
                }
                return;
            }
        }

        context.report(
                ISSUE,
                location,
                "`"
                        + serviceClassName
                        + "` is not registered in the manifest");
    }
}
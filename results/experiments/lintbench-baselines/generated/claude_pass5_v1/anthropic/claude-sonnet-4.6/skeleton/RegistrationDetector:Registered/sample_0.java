package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;

public class RegistrationDetector extends LayoutDetector {

    private static final String ANDROID_APP_ACTIVITY = "android.app.Activity";
    private static final String ANDROID_APP_SERVICE = "android.app.Service";
    private static final String ANDROID_CONTENT_CONTENT_PROVIDER = "android.content.ContentProvider";
    private static final String ANDROID_CONTENT_BROADCAST_RECEIVER = "android.content.BroadcastReceiver";

    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_PROVIDER = "provider";
    private static final String TAG_RECEIVER = "receiver";

    private static final String ATTR_NAME = "name";

    private static final String KEY_CLASS = "class";
    private static final String KEY_MESSAGE = "message";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    RegistrationDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST));

    public static final Issue ISSUE =
            Issue.create(
                    "Registered",
                    "Class is not registered in the manifest",
                    "Activities, services and content providers should be registered in the "
                            + "`AndroidManifest.xml` file using `<activity>`, `<service>` and "
                            + "`<provider>` tags.\n\n"
                            + "If your activity is simply a parent class intended to be "
                            + "subclassed by other \"real\" activities, make it an abstract class.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/guide/topics/manifest/manifest-intro.html");

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                ANDROID_APP_ACTIVITY,
                ANDROID_APP_SERVICE,
                ANDROID_CONTENT_CONTENT_PROVIDER,
                ANDROID_CONTENT_BROADCAST_RECEIVER);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ACTIVITY, TAG_SERVICE, TAG_PROVIDER, TAG_RECEIVER);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android", ATTR_NAME);
        if (name == null || name.isEmpty()) {
            name = element.getAttribute(ATTR_NAME);
        }
        if (name != null && !name.isEmpty()) {
            // Normalize the class name (handle leading dots, etc.)
            String pkg = context.getMainProject().getPackage();
            if (name.startsWith(".")) {
                if (pkg != null) {
                    name = pkg + name;
                }
            } else if (!name.contains(".")) {
                if (pkg != null) {
                    name = pkg + "." + name;
                }
            }
            LintMap map = context.getPartialResults(ISSUE).map();
            // Store registered components with a prefix to avoid collisions
            map.put("registered:" + name, true);
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Skip abstract classes
        PsiClass psiClass = declaration.getJavaPsi();
        if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        // Skip anonymous classes
        if (declaration.getName() == null || declaration.getName().isEmpty()) {
            return;
        }

        // Only check concrete top-level or static nested classes
        String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        // Determine which manifest tag is required
        String tag = getExpectedTag(context, declaration);
        if (tag == null) {
            return;
        }

        // BroadcastReceivers registered dynamically don't need manifest registration
        // but we still check static ones
        if (tag.equals(TAG_RECEIVER)) {
            // Skip receiver check - receivers can be registered dynamically
            return;
        }

        // Store the class info in partial results to be checked later
        LintMap map = context.getPartialResults(ISSUE).map();
        Location location = context.getNameLocation(declaration);

        String message = getUnregisteredMessage(tag, qualifiedName);
        // Store with a unique key per class
        String key = "unregistered:" + qualifiedName;
        map.put(key + ":msg", message);
        map.put(key + ":file", location.getFile().getPath());
        // We can't easily store Location in LintMap, so store coordinates
        if (location.getStart() != null) {
            map.put(key + ":line", location.getStart().getLine());
            map.put(key + ":col", location.getStart().getColumn());
        }
        map.put(key + ":name", qualifiedName);
    }

    private String getExpectedTag(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (context.getEvaluator().extendsClass(declaration, ANDROID_APP_ACTIVITY, false)) {
            return TAG_ACTIVITY;
        } else if (context.getEvaluator().extendsClass(declaration, ANDROID_APP_SERVICE, false)) {
            return TAG_SERVICE;
        } else if (context.getEvaluator().extendsClass(
                declaration, ANDROID_CONTENT_CONTENT_PROVIDER, false)) {
            return TAG_PROVIDER;
        } else if (context.getEvaluator().extendsClass(
                declaration, ANDROID_CONTENT_BROADCAST_RECEIVER, false)) {
            return TAG_RECEIVER;
        }
        return null;
    }

    private String getUnregisteredMessage(String tag, String qualifiedName) {
        String type;
        switch (tag) {
            case TAG_ACTIVITY:
                type = "Activity";
                break;
            case TAG_SERVICE:
                type = "Service";
                break;
            case TAG_PROVIDER:
                type = "ContentProvider";
                break;
            case TAG_RECEIVER:
                type = "BroadcastReceiver";
                break;
            default:
                type = "Component";
                break;
        }
        return type
                + " `"
                + qualifiedName
                + "` is not registered in the manifest";
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        LintMap map = partialResults.mergedMap();

        // Collect all registered component names
        java.util.Set<String> registered = new java.util.HashSet<>();
        for (String key : map) {
            if (key.startsWith("registered:")) {
                String className = key.substring("registered:".length());
                registered.add(className);
            }
        }

        // Check each unregistered candidate
        java.util.Set<String> processed = new java.util.HashSet<>();
        for (String key : map) {
            if (key.startsWith("unregistered:") && key.endsWith(":name")) {
                String className = (String) map.get(key);
                if (className == null) {
                    continue;
                }
                String prefix = "unregistered:" + className;
                if (processed.contains(className)) {
                    continue;
                }
                processed.add(className);

                if (!registered.contains(className)) {
                    String message = (String) map.get(prefix + ":msg");
                    if (message == null) {
                        message = "`" + className + "` is not registered in the manifest";
                    }

                    String filePath = (String) map.get(prefix + ":file");
                    Location location = null;
                    if (filePath != null) {
                        java.io.File file = new java.io.File(filePath);
                        Integer line = (Integer) map.get(prefix + ":line");
                        Integer col = (Integer) map.get(prefix + ":col");
                        if (line != null && col != null) {
                            location = Location.create(
                                    file,
                                    null,
                                    line,
                                    col);
                        } else {
                            location = Location.create(file);
                        }
                    }

                    if (location != null) {
                        context.report(ISSUE, location, message);
                    }
                }
            }
        }
    }
}
package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PACKAGE;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY;
import static com.android.xml.AndroidManifest.NODE_PROVIDER;
import static com.android.xml.AndroidManifest.NODE_RECEIVER;
import static com.android.xml.AndroidManifest.NODE_SERVICE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class RegistrationDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final String ANDROID_APP_ACTIVITY = "android.app.Activity";
    private static final String ANDROID_APP_SERVICE = "android.app.Service";
    private static final String ANDROID_CONTENT_CONTENT_PROVIDER = "android.content.ContentProvider";
    private static final String ANDROID_CONTENT_BROADCAST_RECEIVER = "android.content.BroadcastReceiver";

    private static final String KEY_REGISTERED = "registered";
    private static final String KEY_CLASS = "class";
    private static final String KEY_LOCATION = "location";
    private static final String KEY_TAG = "tag";

    static final Implementation IMPLEMENTATION =
            new Implementation(
                    RegistrationDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "Registered",
                    "Class is not registered in the manifest",
                    "Activities, services and content providers should be registered in the "
                            + "`AndroidManifest.xml` file using `<activity>`, `<service>` and "
                            + "`<provider>` tags.\n"
                            + "\n"
                            + "If your activity is simply a parent class intended to be "
                            + "subclassed by other \"real\" activities, make it an abstract "
                            + "class.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/guide/topics/manifest/manifest-intro.html");

    /** Map from fully-qualified class name to the manifest tag it should be registered under */
    private static final Map<String, String> SUPER_CLASS_TO_TAG = new HashMap<>();

    static {
        SUPER_CLASS_TO_TAG.put(ANDROID_APP_ACTIVITY, NODE_ACTIVITY);
        SUPER_CLASS_TO_TAG.put(ANDROID_APP_SERVICE, NODE_SERVICE);
        SUPER_CLASS_TO_TAG.put(ANDROID_CONTENT_CONTENT_PROVIDER, NODE_PROVIDER);
        SUPER_CLASS_TO_TAG.put(ANDROID_CONTENT_BROADCAST_RECEIVER, NODE_RECEIVER);
    }

    /** Set of class names registered in the manifest */
    private final Set<String> mRegistered = new HashSet<>();

    /** Package name from manifest */
    private String mPackage = null;

    public RegistrationDetector() {}

    // ---- XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_ACTIVITY, NODE_SERVICE, NODE_PROVIDER, NODE_RECEIVER);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Collect the package from the manifest root if not yet collected
        if (mPackage == null) {
            Document doc = element.getOwnerDocument();
            if (doc != null) {
                Element root = doc.getDocumentElement();
                if (root != null) {
                    String pkg = root.getAttribute(ATTR_PACKAGE);
                    if (pkg != null && !pkg.isEmpty()) {
                        mPackage = pkg;
                    }
                }
            }
        }

        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        // Resolve relative names
        String fqcn = resolveName(name, mPackage);
        if (fqcn != null) {
            mRegistered.add(fqcn);
        }

        // Also store in partial results for multi-module support
        LintMap map = context.getPartialResults(ISSUE).map();
        String key = KEY_REGISTERED + ":" + (fqcn != null ? fqcn : name);
        map.put(key, true);
    }

    @Nullable
    private static String resolveName(@NonNull String name, @Nullable String pkg) {
        if (name.startsWith(".")) {
            // Relative name
            if (pkg != null) {
                return pkg + name;
            }
            return name;
        } else if (!name.contains(".")) {
            // Simple name without dots — prepend package
            if (pkg != null) {
                return pkg + "." + name;
            }
            return name;
        }
        return name;
    }

    // ---- SourceCodeScanner ----

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                ANDROID_APP_ACTIVITY,
                ANDROID_APP_SERVICE,
                ANDROID_CONTENT_CONTENT_PROVIDER,
                ANDROID_CONTENT_BROADCAST_RECEIVER);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Skip abstract classes — they are not expected to be registered
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        // Skip anonymous classes
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        // BroadcastReceivers are often registered dynamically, so we skip them
        // unless they are explicitly in the manifest. We still check the others.
        if (extendsClass(context, declaration, ANDROID_CONTENT_BROADCAST_RECEIVER)) {
            // Dynamic receivers are common; skip checking them
            return;
        }

        // Determine which manifest tag is expected
        String expectedTag = getExpectedTag(context, declaration);
        if (expectedTag == null) {
            return;
        }

        // Store information for partial results so cross-module checks work
        LintMap partialMap = context.getPartialResults(ISSUE).map();
        String classKey = KEY_CLASS + ":" + qualifiedName;

        // Check if registered in the manifest (within the same module)
        if (!mRegistered.contains(qualifiedName)) {
            // Store for deferred check via checkPartialResults
            partialMap.put(classKey, qualifiedName);
            partialMap.put(KEY_TAG + ":" + qualifiedName, expectedTag);

            Location location = context.getNameLocation(declaration);
            // Report immediately if we can determine it's not registered
            // (single-module analysis)
            if (!context.getDriver().isIsolated()) {
                reportIfNotRegistered(context, declaration, qualifiedName, expectedTag, location);
            } else {
                // Store location info for partial results
                partialMap.put(KEY_LOCATION + ":" + qualifiedName, location);
            }
        }
    }

    private void reportIfNotRegistered(
            @NonNull JavaContext context,
            @NonNull UClass declaration,
            @NonNull String qualifiedName,
            @NonNull String expectedTag,
            @NonNull Location location) {
        if (!mRegistered.contains(qualifiedName)) {
            String message =
                    String.format(
                            "The `<%1$s>` `%2$s` is not registered in the manifest",
                            expectedTag, qualifiedName);
            context.report(ISSUE, declaration, location, message);
        }
    }

    @Nullable
    private static String getExpectedTag(
            @NonNull JavaContext context, @NonNull UClass declaration) {
        for (Map.Entry<String, String> entry : SUPER_CLASS_TO_TAG.entrySet()) {
            if (extendsClass(context, declaration, entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static boolean extendsClass(
            @NonNull JavaContext context,
            @NonNull UClass declaration,
            @NonNull String superClassName) {
        PsiClass superClass = context.getEvaluator().findClass(superClassName);
        if (superClass == null) {
            return false;
        }
        return context.getEvaluator().extendsClass(declaration, superClassName, false);
    }

    // ---- PartialResults ----

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        // Collect all registered classes and all candidate classes from partial results
        Set<String> registeredClasses = new HashSet<>();
        Map<String, String> candidateToTag = new HashMap<>();

        for (Map.Entry<String, LintMap> entry : partialResults) {
            LintMap map = entry.getValue();
            for (String key : map) {
                if (key.startsWith(KEY_REGISTERED + ":")) {
                    String className = key.substring((KEY_REGISTERED + ":").length());
                    registeredClasses.add(className);
                } else if (key.startsWith(KEY_CLASS + ":")) {
                    String className = map.getString(key, null);
                    if (className != null) {
                        String tagKey = KEY_TAG + ":" + className;
                        String tag = map.getString(tagKey, null);
                        if (tag != null) {
                            candidateToTag.put(className, tag);
                        }
                    }
                }
            }
        }

        // Report any candidates not found in the registered set
        for (Map.Entry<String, String> candidate : candidateToTag.entrySet()) {
            String className = candidate.getKey();
            String tag = candidate.getValue();
            if (!registeredClasses.contains(className)) {
                String message =
                        String.format(
                                "The `<%1$s>` `%2$s` is not registered in the manifest",
                                tag, className);
                context.report(ISSUE, Location.create(context.file), message);
            }
        }
    }
}
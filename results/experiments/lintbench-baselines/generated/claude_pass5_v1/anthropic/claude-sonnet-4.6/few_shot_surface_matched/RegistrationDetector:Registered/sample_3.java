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
import com.android.tools.lint.detector.api.ClassContext;
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
import java.util.List;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

public class RegistrationDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    RegistrationDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE),
                    Scope.JAVA_FILE_SCOPE);

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
            .addMoreInfo("https://developer.android.com/guide/topics/manifest/manifest-intro.html");

    private static final String ANDROID_APP_ACTIVITY = "android.app.Activity";
    private static final String ANDROID_APP_SERVICE = "android.app.Service";
    private static final String ANDROID_CONTENT_CONTENT_PROVIDER = "android.content.ContentProvider";
    private static final String ANDROID_CONTENT_BROADCAST_RECEIVER = "android.content.BroadcastReceiver";

    private static final String KEY_CLASS = "class";
    private static final String KEY_TAG = "tag";

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
        // Skip abstract classes — they are not meant to be registered directly
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        // Skip anonymous or local classes
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        // Determine which manifest tag we expect
        String tag = getExpectedTag(context, declaration);
        if (tag == null) {
            return;
        }

        // In partial analysis mode, store info for checkPartialResults
        if (context.isGlobalAnalysis()) {
            // Check manifest directly if we have it
            if (!isRegistered(context, qualifiedName, tag)) {
                Location location = context.getNameLocation(declaration);
                String message = getErrorMessage(qualifiedName, tag);
                context.report(ISSUE, declaration, location, message);
            }
        } else {
            // Partial analysis: record the class and its expected tag
            LintMap map = context.getPartialResults(ISSUE).map();
            map.put(qualifiedName, tag);
            // Also store the location
            Location location = context.getNameLocation(declaration);
            map.put(qualifiedName + ":location", location);
        }
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        // Collect all registered component names from the manifest
        java.util.Set<String> registered = collectRegisteredComponents(context);

        LintMap map = partialResults.map();
        for (String key : map) {
            if (key.endsWith(":location")) {
                continue;
            }
            String qualifiedName = key;
            String tag = map.getString(key, null);
            if (tag == null) {
                continue;
            }
            if (!registered.contains(qualifiedName)) {
                Object locationObj = map.get(qualifiedName + ":location");
                Location location = null;
                if (locationObj instanceof Location) {
                    location = (Location) locationObj;
                }
                String message = getErrorMessage(qualifiedName, tag);
                if (location != null) {
                    context.report(ISSUE, location, message);
                }
            }
        }
    }

    @Nullable
    private String getExpectedTag(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (context.getEvaluator().extendsClass(declaration, ANDROID_APP_ACTIVITY, false)) {
            return NODE_ACTIVITY;
        } else if (context.getEvaluator().extendsClass(declaration, ANDROID_APP_SERVICE, false)) {
            return NODE_SERVICE;
        } else if (context.getEvaluator().extendsClass(declaration, ANDROID_CONTENT_CONTENT_PROVIDER, false)) {
            return NODE_PROVIDER;
        } else if (context.getEvaluator().extendsClass(declaration, ANDROID_CONTENT_BROADCAST_RECEIVER, false)) {
            return NODE_RECEIVER;
        }
        return null;
    }

    private boolean isRegistered(
            @NonNull JavaContext context,
            @NonNull String qualifiedName,
            @NonNull String tag) {
        java.util.Set<String> registered = collectRegisteredComponents(context);
        return registered.contains(qualifiedName);
    }

    private java.util.Set<String> collectRegisteredComponents(@NonNull Context context) {
        java.util.Set<String> registered = new java.util.HashSet<>();

        // Try to get the manifest file
        com.android.tools.lint.detector.api.Project project = context.getProject();
        java.io.File manifestFile = project.getManifestFiles().isEmpty()
                ? null
                : project.getManifestFiles().get(0);

        if (manifestFile == null || !manifestFile.exists()) {
            return registered;
        }

        // Parse the manifest
        try {
            javax.xml.parsers.DocumentBuilderFactory factory =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(manifestFile);

            // Get the package name
            String packageName = "";
            Element manifestElement = document.getDocumentElement();
            if (manifestElement != null) {
                String pkg = manifestElement.getAttribute(ATTR_PACKAGE);
                if (pkg != null) {
                    packageName = pkg;
                }
            }

            // Collect all registered components
            collectFromTag(document, NODE_ACTIVITY, packageName, registered);
            collectFromTag(document, NODE_SERVICE, packageName, registered);
            collectFromTag(document, NODE_PROVIDER, packageName, registered);
            collectFromTag(document, NODE_RECEIVER, packageName, registered);

        } catch (Exception e) {
            // If we can't parse the manifest, return empty set
        }

        return registered;
    }

    private void collectFromTag(
            @NonNull Document document,
            @NonNull String tag,
            @NonNull String packageName,
            @NonNull java.util.Set<String> registered) {
        NodeList elements = document.getElementsByTagName(tag);
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (name == null || name.isEmpty()) {
                name = element.getAttribute(ATTR_NAME);
            }
            if (name != null && !name.isEmpty()) {
                String resolved = resolveClassName(name, packageName);
                registered.add(resolved);
            }
        }
    }

    @NonNull
    private String resolveClassName(@NonNull String name, @NonNull String packageName) {
        if (name.startsWith(".")) {
            return packageName + name;
        } else if (!name.contains(".")) {
            return packageName + "." + name;
        }
        return name;
    }

    @NonNull
    private String getErrorMessage(@NonNull String qualifiedName, @NonNull String tag) {
        String simpleName = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
        String elementDescription;
        switch (tag) {
            case NODE_ACTIVITY:
                elementDescription = "activity";
                break;
            case NODE_SERVICE:
                elementDescription = "service";
                break;
            case NODE_PROVIDER:
                elementDescription = "content provider";
                break;
            case NODE_RECEIVER:
                elementDescription = "broadcast receiver";
                break;
            default:
                elementDescription = "component";
                break;
        }
        return "The `" + simpleName + "` " + elementDescription
                + " is not registered in the manifest";
    }
}
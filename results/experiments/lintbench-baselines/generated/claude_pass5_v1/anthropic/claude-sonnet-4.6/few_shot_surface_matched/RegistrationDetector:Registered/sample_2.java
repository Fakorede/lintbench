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
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class RegistrationDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    RegistrationDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE),
                    Scope.MANIFEST_SCOPE,
                    Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                            "Registered",
                            "Class is not registered in the manifest",
                            "Activities, services and content providers should be registered in the"
                                    + " `AndroidManifest.xml` file using `<activity>`, `<service>`"
                                    + " and `<provider>` tags.\n"
                                    + "\n"
                                    + "If your activity is simply a parent class intended to be"
                                    + " subclassed by other \"real\" activities, make it an abstract"
                                    + " class.",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/guide/topics/manifest/manifest-intro.html");

    private static final String ANDROID_APP_ACTIVITY = "android.app.Activity";
    private static final String ANDROID_APP_SERVICE = "android.app.Service";
    private static final String ANDROID_CONTENT_CONTENT_PROVIDER = "android.content.ContentProvider";
    private static final String ANDROID_CONTENT_BROADCAST_RECEIVER =
            "android.content.BroadcastReceiver";

    private static final String KEY_CLASSES = "classes";
    private static final String KEY_LOCATIONS = "locations";

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
        // Skip abstract classes
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        // Skip anonymous classes
        if (declaration.getName() == null) {
            return;
        }

        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        // Determine the tag that should be used for this class
        String expectedTag = getExpectedTag(context, declaration);
        if (expectedTag == null) {
            return;
        }

        // In single-file analysis mode, we store partial results for later
        LintMap map = context.getPartialResults(ISSUE).map();
        String locationKey = qualifiedName + "_location";
        String tagKey = qualifiedName + "_tag";

        Location location = context.getNameLocation(declaration);
        map.put(locationKey, location);
        map.put(tagKey, expectedTag);

        // Also accumulate the list of class names
        String existing = map.getString(KEY_CLASSES, null);
        if (existing == null) {
            map.put(KEY_CLASSES, qualifiedName);
        } else {
            map.put(KEY_CLASSES, existing + "," + qualifiedName);
        }
    }

    @Nullable
    private String getExpectedTag(
            @NonNull JavaContext context, @NonNull UClass declaration) {
        PsiClass activityClass = context.getEvaluator().findClass(ANDROID_APP_ACTIVITY);
        PsiClass serviceClass = context.getEvaluator().findClass(ANDROID_APP_SERVICE);
        PsiClass providerClass =
                context.getEvaluator().findClass(ANDROID_CONTENT_CONTENT_PROVIDER);
        PsiClass receiverClass =
                context.getEvaluator().findClass(ANDROID_CONTENT_BROADCAST_RECEIVER);

        if (activityClass != null
                && context.getEvaluator().extendsClass(declaration, ANDROID_APP_ACTIVITY, false)) {
            return NODE_ACTIVITY;
        } else if (serviceClass != null
                && context.getEvaluator().extendsClass(declaration, ANDROID_APP_SERVICE, false)) {
            return NODE_SERVICE;
        } else if (providerClass != null
                && context.getEvaluator()
                        .extendsClass(declaration, ANDROID_CONTENT_CONTENT_PROVIDER, false)) {
            return NODE_PROVIDER;
        } else if (receiverClass != null
                && context.getEvaluator()
                        .extendsClass(declaration, ANDROID_CONTENT_BROADCAST_RECEIVER, false)) {
            return NODE_RECEIVER;
        }
        return null;
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        // Collect all registered class names from the manifest
        java.util.Set<String> registeredClasses = new java.util.HashSet<>();
        String manifestPackage = "";

        // Find the manifest file and parse registered components
        com.android.tools.lint.detector.api.Project project = context.getProject();
        java.io.File manifestFile = project.getManifestFiles().isEmpty()
                ? null
                : project.getManifestFiles().get(0);

        if (manifestFile != null && manifestFile.exists()) {
            try {
                javax.xml.parsers.DocumentBuilderFactory factory =
                        javax.xml.parsers.DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
                Document document = builder.parse(manifestFile);
                Element root = document.getDocumentElement();
                if (root != null) {
                    manifestPackage = root.getAttribute(ATTR_PACKAGE);
                    if (manifestPackage == null) {
                        manifestPackage = "";
                    }
                    collectRegistered(root, manifestPackage, registeredClasses, NODE_ACTIVITY);
                    collectRegistered(root, manifestPackage, registeredClasses, NODE_SERVICE);
                    collectRegistered(root, manifestPackage, registeredClasses, NODE_PROVIDER);
                    collectRegistered(root, manifestPackage, registeredClasses, NODE_RECEIVER);
                }
            } catch (Exception e) {
                // If we can't parse the manifest, skip the check
                return;
            }
        }

        // Now check each partial result map
        for (LintMap map : partialResults.maps()) {
            String classesStr = map.getString(KEY_CLASSES, null);
            if (classesStr == null || classesStr.isEmpty()) {
                continue;
            }
            String[] classes = classesStr.split(",");
            for (String qualifiedName : classes) {
                if (qualifiedName.isEmpty()) {
                    continue;
                }
                if (!isRegistered(qualifiedName, registeredClasses)) {
                    String locationKey = qualifiedName + "_location";
                    String tagKey = qualifiedName + "_tag";
                    Location location = map.getLocation(locationKey);
                    String tag = map.getString(tagKey, "component");
                    if (location != null) {
                        String tagLabel = getTagLabel(tag);
                        context.report(
                                ISSUE,
                                location,
                                "`"
                                        + qualifiedName
                                        + "` is not registered in the manifest");
                    }
                }
            }
        }
    }

    private void collectRegistered(
            @NonNull Element root,
            @NonNull String packageName,
            @NonNull java.util.Set<String> registeredClasses,
            @NonNull String tagName) {
        NodeList nodes = root.getElementsByTagName(tagName);
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (name == null || name.isEmpty()) {
                name = element.getAttribute(ATTR_NAME);
            }
            if (name != null && !name.isEmpty()) {
                if (name.startsWith(".")) {
                    name = packageName + name;
                } else if (!name.contains(".")) {
                    name = packageName + "." + name;
                }
                registeredClasses.add(name);
            }
        }
    }

    private boolean isRegistered(
            @NonNull String qualifiedName, @NonNull java.util.Set<String> registeredClasses) {
        return registeredClasses.contains(qualifiedName);
    }

    @NonNull
    private String getTagLabel(@Nullable String tag) {
        if (tag == null) {
            return "component";
        }
        switch (tag) {
            case NODE_ACTIVITY:
                return "activity";
            case NODE_SERVICE:
                return "service";
            case NODE_PROVIDER:
                return "content provider";
            case NODE_RECEIVER:
                return "broadcast receiver";
            default:
                return "component";
        }
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return null;
    }
}
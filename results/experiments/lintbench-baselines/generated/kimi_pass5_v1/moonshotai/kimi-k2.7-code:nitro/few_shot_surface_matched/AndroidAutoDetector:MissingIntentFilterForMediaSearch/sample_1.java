package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.*;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String MEDIA_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String MEDIA_BROWSER_SERVICE = "android.media.browse.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_COMPAT = "android.support.v4.media.MediaBrowserServiceCompat";
    private static final String MEDIA_BROWSER_SERVICE_COMPATX = "androidx.media.MediaBrowserServiceCompat";

    public static final Issue ISSUE =
            Issue.create(
                    "MissingIntentFilterForMediaSearch",
                    "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
                    "To support voice searches on Android Auto, you should also register an "
                            + "`<intent-filter>` for the action "
                            + "`android.media.action.MEDIA_PLAY_FROM_SEARCH`. Add an "
                            + "`<intent-filter>` containing "
                            + "`<action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />` "
                            + "to the relevant `<activity>` or `<service>`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            java.util.EnumSet.of(Scope.MANIFEST_SCOPE, Scope.JAVA_FILE_SCOPE)));

    private final java.util.Set<String> mMediaServiceClasses = new java.util.HashSet<>();

    public AndroidAutoDetector() {}

    @Override
    public boolean appliesTo(com.android.tools.lint.detector.api.Context context, java.io.File file) {
        return true;
    }

    @Override
    public void beforeCheckRootProject(com.android.tools.lint.detector.api.Context context) {
        mMediaServiceClasses.clear();
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Arrays.asList("service");
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        if (!"service".equals(element.getTagName())) {
            return;
        }

        boolean isMediaService = false;
        boolean hasSearchFilter = false;

        String className = element.getAttributeNS(ANDROID_URI, "name");
        if (className != null && !className.isEmpty()) {
            String pkg = context.getManifestPackage();
            if (pkg != null && !pkg.isEmpty()) {
                String resolved;
                if (className.startsWith(".")) {
                    resolved = pkg + className;
                } else if (className.contains(".")) {
                    resolved = className;
                } else {
                    resolved = pkg + "." + className;
                }
                if (mMediaServiceClasses.contains(resolved)) {
                    isMediaService = true;
                }
            }
        }

        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node node = children.item(i);
            if (node.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            org.w3c.dom.Element child = (org.w3c.dom.Element) node;
            if (!"intent-filter".equals(child.getTagName())) {
                continue;
            }
            org.w3c.dom.NodeList actions = child.getElementsByTagName("action");
            for (int j = 0; j < actions.getLength(); j++) {
                org.w3c.dom.Element action = (org.w3c.dom.Element) actions.item(j);
                String actionName = action.getAttributeNS(ANDROID_URI, "name");
                if (MEDIA_PLAY_FROM_SEARCH.equals(actionName)) {
                    hasSearchFilter = true;
                } else if (MEDIA_BROWSER_SERVICE.equals(actionName)) {
                    isMediaService = true;
                }
            }
        }

        if (isMediaService && !hasSearchFilter) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing intent-filter for media search: add an "
                            + "`<action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />` "
                            + "intent-filter to this service.");
        }
    }

    @Override
    public java.util.List<String> applicableSuperClasses() {
        return java.util.Arrays.asList(
                MEDIA_BROWSER_SERVICE, MEDIA_BROWSER_SERVICE_COMPAT, MEDIA_BROWSER_SERVICE_COMPATX);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        String name = declaration.getQualifiedName();
        if (name != null) {
            mMediaServiceClasses.add(name);
        }
    }

    @Override
    public void visitMethod(JavaContext context, UCallExpression node, PsiMethod method) {
        // Not needed for this manifest-centric check.
    }
}
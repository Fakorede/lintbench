package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String MEDIA_PLAY_FROM_SEARCH_ACTION =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingIntentFilterForMediaSearch",
                    "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
                    "To support voice searches on Android Auto, a media browser service must also "
                            + "declare an intent filter with the action "
                            + MEDIA_PLAY_FROM_SEARCH_ACTION
                            + ". Add an <intent-filter> containing an "
                            + "<action android:name=\""
                            + MEDIA_PLAY_FROM_SEARCH_ACTION
                            + "\" /> element to the relevant <service> or <activity>.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private String mPackageName;
    private final Set<String> mMediaBrowserServices = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MANIFEST;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("manifest", "service", "activity");
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.service.media.MediaBrowserService",
                "android.support.v4.media.MediaBrowserServiceCompat",
                "androidx.media.MediaBrowserServiceCompat");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mPackageName = null;
        mMediaBrowserServices.clear();
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName != null) {
            mMediaBrowserServices.add(qualifiedName);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if ("manifest".equals(tag)) {
            mPackageName = element.getAttribute("package");
            return;
        }

        if (!"service".equals(tag) && !"activity".equals(tag)) {
            return;
        }

        if (mPackageName == null) {
            return;
        }

        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (name == null || name.isEmpty()) {
            return;
        }

        String fqcn = resolveComponentName(name, mPackageName);
        boolean isMediaBrowserService =
                mMediaBrowserServices.contains(fqcn)
                        || hasAction(element, MEDIA_BROWSER_SERVICE_ACTION);

        if (!isMediaBrowserService) {
            return;
        }

        if (!hasAction(element, MEDIA_PLAY_FROM_SEARCH_ACTION)) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing intent filter for voice searches. Add an <intent-filter> with "
                            + "<action android:name=\""
                            + MEDIA_PLAY_FROM_SEARCH_ACTION
                            + "\" /> to this component.");
        }
    }

    private void visitMethod(@NonNull JavaContext context, @NonNull UClass declaringClass) {
        // No method-level checks are required for this issue.
    }

    private static boolean hasAction(@NonNull Element component, @NonNull String action) {
        NodeList filters = component.getElementsByTagName("intent-filter");
        for (int i = 0; i < filters.getLength(); i++) {
            Element filter = (Element) filters.item(i);
            NodeList actions = filter.getElementsByTagName("action");
            for (int j = 0; j < actions.getLength(); j++) {
                Element actionElement = (Element) actions.item(j);
                String actionName = actionElement.getAttributeNS(ANDROID_URI, "name");
                if (action.equals(actionName)) {
                    return true;
                }
            }
        }
        return false;
    }

    @NonNull
    private static String resolveComponentName(@NonNull String name, @NonNull String packageName) {
        if (name.startsWith(".")) {
            return packageName + name;
        } else if (name.indexOf('.') >= 0) {
            return name;
        } else {
            return packageName + "." + name;
        }
    }
}
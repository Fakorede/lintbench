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
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements Detector.SourceCodeScanner, Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingIntentFilterForMediaSearch",
                    "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
                    "To support voice searches on Android Auto, you should also register an intent-filter for the action android.media.action.MEDIA_PLAY_FROM_SEARCH.\n\n"
                    + "To do this, add:\n"
                    + "<intent-filter>\n"
                    + "    <action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />\n"
                    + "</intent-filter>\n"
                    + "to your <activity> or <service>.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String MEDIA_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    private Set<String> mMediaServices = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MANIFEST;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("activity", "service");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mMediaServices.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (name == null || name.isEmpty()) {
            return;
        }

        if (!isMediaService(context, name)) {
            return;
        }

        if (!hasMediaPlayFromSearchIntentFilter(element)) {
            context.report(ISSUE, element, context.getLocation(element),
                    "To support voice searches on Android Auto, you should also register an intent-filter for the action android.media.action.MEDIA_PLAY_FROM_SEARCH.");
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.service.media.MediaBrowserService",
                "android.support.v4.media.MediaBrowserServiceCompat",
                "androidx.media.MediaBrowserServiceCompat"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String fqn = declaration.getQualifiedName();
        if (fqn != null) {
            mMediaServices.add(fqn);
        }
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        // No method-level inspection required for this issue.
    }

    private boolean isMediaService(@NonNull XmlContext context, @NonNull String name) {
        String pkg = context.getProject().getPackage();
        String fqn = name;
        if (pkg != null) {
            if (name.startsWith(".")) {
                fqn = pkg + name;
            } else if (name.indexOf('.') == -1) {
                fqn = pkg + "." + name;
            }
        }
        return mMediaServices.contains(fqn);
    }

    private boolean hasMediaPlayFromSearchIntentFilter(@NonNull Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "intent-filter".equals(child.getNodeName())) {
                Element filter = (Element) child;
                NodeList actions = filter.getElementsByTagName("action");
                for (int j = 0; j < actions.getLength(); j++) {
                    Node actionNode = actions.item(j);
                    if (actionNode.getNodeType() == Node.ELEMENT_NODE) {
                        String actionName = ((Element) actionNode).getAttributeNS(ANDROID_URI, "name");
                        if (MEDIA_PLAY_FROM_SEARCH.equals(actionName)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
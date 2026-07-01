package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String MEDIA_BROWSER_SERVICE = "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_COMPAT = "androidx.media.MediaBrowserServiceCompat";

    public static final Issue ISSUE = Issue.create(
            "MissingIntentFilterForMediaSearch",
            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
            "To support voice searches on Android Auto, you should also register an intent-filter " +
            "for the action android.media.action.MEDIA_PLAY_FROM_SEARCH. Add <intent-filter> with " +
            "<action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" /> to your <activity> or <service>.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.MANIFEST_SCOPE)));

    private Set<String> mMediaComponents;
    private Set<String> mComponentsWithCallback;

    @Override
    public boolean appliesTo(@NonNull Scope scope) {
        return scope == Scope.MANIFEST_SCOPE || scope == Scope.JAVA_FILE_SCOPE;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("activity", "service");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mMediaComponents = new HashSet<>();
        mComponentsWithCallback = new HashSet<>();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (!"service".equals(tag) && !"activity".equals(tag)) {
            return;
        }

        boolean hasFilter = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "intent-filter".equals(child.getNodeName())) {
                Element filter = (Element) child;
                NodeList actions = filter.getElementsByTagName("action");
                for (int j = 0; j < actions.getLength(); j++) {
                    Element action = (Element) actions.item(j);
                    if (ACTION_MEDIA_PLAY_FROM_SEARCH.equals(action.getAttributeNS(ANDROID_URI, "name"))) {
                        hasFilter = true;
                        break;
                    }
                }
            }
            if (hasFilter) break;
        }

        if (!hasFilter) {
            context.report(ISSUE, element, context.getLocation(element),
                    "To support voice searches on Android Auto, register an intent-filter for " +
                    "`android.media.action.MEDIA_PLAY_FROM_SEARCH`");
        }
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(MEDIA_BROWSER_SERVICE, MEDIA_BROWSER_SERVICE_COMPAT);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName != null) {
            mMediaComponents.add(qualifiedName);
        }
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod node) {
        if ("onPlayFromSearch".equals(node.getName())) {
            UClass containingClass = node.getContainingClass();
            if (containingClass != null && containingClass.getQualifiedName() != null) {
                mComponentsWithCallback.add(containingClass.getQualifiedName());
            }
        }
    }
}
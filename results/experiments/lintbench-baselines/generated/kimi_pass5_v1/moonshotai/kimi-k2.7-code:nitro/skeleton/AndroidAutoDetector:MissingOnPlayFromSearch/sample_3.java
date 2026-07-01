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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String MEDIA_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String ON_PLAY_FROM_SEARCH = "onPlayFromSearch";
    private static final String PARAM_QUERY = "java.lang.String";
    private static final String PARAM_EXTRAS = "android.os.Bundle";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";
    private static final String TAG_ACTION = "action";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_SERVICE = "service";

    private static final List<String> MEDIA_BROWSER_SERVICES =
            Collections.unmodifiableList(
                    Arrays.asList(
                            "android.media.browse.MediaBrowserService",
                            "android.support.v4.media.MediaBrowserServiceCompat",
                            "androidx.media.MediaBrowserServiceCompat"));

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingOnPlayFromSearch",
                    "Missing `onPlayFromSearch`",
                    "To support voice searches on Android Auto, in addition to adding an "
                            + "intent-filter for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH`, "
                            + "you also need to override and implement "
                            + "`onPlayFromSearch(String query, Bundle bundle)` in your MediaBrowserService.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasPlayFromSearchIntent;
    private final List<ClassInfo> mMissingServices = new ArrayList<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_ACTION);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasPlayFromSearchIntent = false;
        mMissingServices.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!TAG_ACTION.equals(element.getTagName())) {
            return;
        }

        String action = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (action.isEmpty()) {
            action = element.getAttribute(ATTR_NAME);
        }
        if (!MEDIA_PLAY_FROM_SEARCH.equals(action)) {
            return;
        }

        Node parent = element.getParentNode();
        if (parent == null || !TAG_INTENT_FILTER.equals(parent.getNodeName())) {
            return;
        }

        Node grandparent = parent.getParentNode();
        if (grandparent == null || !TAG_SERVICE.equals(grandparent.getNodeName())) {
            return;
        }

        mHasPlayFromSearchIntent = true;
    }

    @Override
    public List<String> applicableSuperClasses() {
        return MEDIA_BROWSER_SERVICES;
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.isAbstract()) {
            return;
        }

        if (!hasOnPlayFromSearch(declaration)) {
            mMissingServices.add(new ClassInfo(context, declaration));
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (!mHasPlayFromSearchIntent || mMissingServices.isEmpty()) {
            return;
        }

        for (ClassInfo info : mMissingServices) {
            info.context.report(
                    ISSUE,
                    info.declaration,
                    info.context.getLocation(info.declaration),
                    "Must override `onPlayFromSearch(String, Bundle)` to support voice searches on Android Auto");
        }
    }

    private static boolean hasOnPlayFromSearch(@NonNull UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if (!ON_PLAY_FROM_SEARCH.equals(method.getName())) {
                continue;
            }

            List<UParameter> parameters = method.getUastParameters();
            if (parameters.size() != 2) {
                continue;
            }

            String first = parameters.get(0).getType().getCanonicalText();
            String second = parameters.get(1).getType().getCanonicalText();
            if (PARAM_QUERY.equals(first) && PARAM_EXTRAS.equals(second)) {
                return true;
            }
        }
        return false;
    }

    private static class ClassInfo {
        final JavaContext context;
        final UClass declaration;

        ClassInfo(JavaContext context, UClass declaration) {
            this.context = context;
            this.declaration = declaration;
        }
    }
}
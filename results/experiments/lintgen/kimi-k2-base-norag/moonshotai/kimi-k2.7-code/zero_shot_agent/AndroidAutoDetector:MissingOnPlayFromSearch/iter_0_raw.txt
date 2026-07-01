package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class AndroidAutoDetector extends Detector implements XmlScanner, Detector.ClassScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "name";

    private static final String ACTION_PLAY_FROM_SEARCH = "android.media.action.PLAY_FROM_SEARCH";
    private static final String ACTION_MEDIA_BROWSER = "android.media.browse.MediaBrowserService";

    private static final String METHOD_ON_PLAY_FROM_SEARCH = "onPlayFromSearch";
    private static final String DESC_ON_PLAY_FROM_SEARCH = "(Ljava/lang/String;Landroid/os/Bundle;)V";

    public static final Issue ISSUE = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing onPlayFromSearch",
            "To support voice searches on Android Auto, in addition to adding an "
                    + "intent-filter for the voice search action, you must override and "
                    + "implement onPlayFromSearch(String query, Bundle bundle) in your "
                    + "MediaSession.Callback.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.CLASS_FILE)
            ),
            "https://developer.android.com/training/auto/audio/index.html#support_voice"
    );

    private boolean mHasPlayFromSearchFilter;
    private boolean mHasOnPlayFromSearch;
    private Location mManifestLocation;

    @Override
    public void beforeCheckProject(Context context) {
        mHasPlayFromSearchFilter = false;
        mHasOnPlayFromSearch = false;
        mManifestLocation = null;
    }

    @Override
    public void afterCheckProject(Context context) {
        if (mHasPlayFromSearchFilter && !mHasOnPlayFromSearch && mManifestLocation != null) {
            context.report(ISSUE, mManifestLocation,
                    "Must override and implement onPlayFromSearch(String, Bundle)");
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_ACTION);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!TAG_ACTION.equals(element.getTagName())) {
            return;
        }

        String actionName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (ACTION_PLAY_FROM_SEARCH.equals(actionName)
                || ACTION_MEDIA_BROWSER.equals(actionName)) {
            mHasPlayFromSearchFilter = true;
            mManifestLocation = context.getLocation(element);
        }
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android/media/session/MediaSession$Callback",
                "android/support/v4/media/session/MediaSessionCompat$Callback",
                "androidx/media/session/MediaSessionCompat$Callback"
        );
    }

    @Override
    public void visitClass(JavaContext context, ClassNode classNode) {
    }

    @Override
    public void visitMethod(JavaContext context, MethodNode methodNode, ClassNode classNode) {
        if (METHOD_ON_PLAY_FROM_SEARCH.equals(methodNode.name)
                && DESC_ON_PLAY_FROM_SEARCH.equals(methodNode.desc)) {
            mHasOnPlayFromSearch = true;
        }
    }
}
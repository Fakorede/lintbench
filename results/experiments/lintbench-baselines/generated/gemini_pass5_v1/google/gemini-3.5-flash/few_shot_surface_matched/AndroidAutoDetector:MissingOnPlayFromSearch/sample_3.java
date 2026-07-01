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
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UCallExpression;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue MISSING_ON_PLAY_FROM_SEARCH =
            Issue.create(
                    "MissingOnPlayFromSearch",
                    "Missing onPlayFromSearch",
                    "To support voice searches on Android Auto, in addition to adding "
                            + "an `intent-filter` for the action `onPlayFromSearch`, you also "
                            + "need to override and implement `onPlayFromSearch(String query, Bundle bundle)`",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)));

    private static final String ACTION_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    private static final String MEDIA_SESSION_CALLBACK =
            "android.media.session.MediaSession.Callback";
    private static final String MEDIA_SESSION_COMPAT_CALLBACK =
            "android.support.v4.media.session.MediaSessionCompat.Callback";
    private static final String MEDIA_SESSION_COMPAT_CALLBACK_ANDROIDX =
            "androidx.media.MediaSessionCompat.Callback";

    private boolean mHasVoiceIntentFilter;
    private boolean mHasOnPlayFromSearch;
    private final List<Location> mVoiceIntentFilters = new ArrayList<>();

    @Override
    public boolean appliesTo(Context context, File file) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("action");
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        mHasVoiceIntentFilter = false;
        mHasOnPlayFromSearch = false;
        mVoiceIntentFilters.clear();
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
        if (ACTION_PLAY_FROM_SEARCH.equals(name)) {
            mHasVoiceIntentFilter = true;
            mVoiceIntentFilters.add(context.getLocation(element));
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        List<String> list = new ArrayList<>();
        list.add(MEDIA_SESSION_CALLBACK);
        list.add(MEDIA_SESSION_COMPAT_CALLBACK);
        list.add(MEDIA_SESSION_COMPAT_CALLBACK_ANDROIDX);
        return list;
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        for (PsiMethod method : declaration.getMethods()) {
            if ("onPlayFromSearch".equals(method.getName())) {
                mHasOnPlayFromSearch = true;
                break;
            }
        }
    }

    @Override
    public void visitMethod(JavaContext context, UCallExpression node, PsiMethod method) {
        // Required override to satisfy interface contract
    }

    @Override
    public void afterCheckRootProject(Context context) {
        if (mHasVoiceIntentFilter && !mHasOnPlayFromSearch) {
            for (Location location : mVoiceIntentFilters) {
                context.report(
                        MISSING_ON_PLAY_FROM_SEARCH,
                        location,
                        "To support voice searches, you must also override and implement "
                                + "`onPlayFromSearch(String query, Bundle bundle)`");
            }
        }
    }
}
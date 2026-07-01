package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
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
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingOnPlayFromSearch",
                    "Missing `onPlayFromSearch`",
                    "To support voice searches on Android Auto, in addition to adding an " +
                    "intent-filter for the action `onPlayFromSearch`, you also need to override " +
                    "and implement `onPlayFromSearch(String query, Bundle bundle)`",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasIntentFilter = false;
    private Location mIntentFilterLocation = null;
    private boolean mHasOnPlayFromSearch = false;
    private final List<Location> mCallbackLocationsWithoutOnPlayFromSearch = new java.util.ArrayList<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("action");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasIntentFilter = false;
        mIntentFilterLocation = null;
        mHasOnPlayFromSearch = false;
        mCallbackLocationsWithoutOnPlayFromSearch.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if ("action".equals(element.getTagName())) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(name)) {
                mHasIntentFilter = true;
                mIntentFilterLocation = context.getLocation(element);
            }
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return java.util.Arrays.asList(
                "android.media.session.MediaSession.Callback",
                "android.support.v4.media.session.MediaSessionCompat.Callback"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        boolean overridesOnPlayFromSearch = false;
        for (org.jetbrains.uast.UMethod method : declaration.getMethods()) {
            if ("onPlayFromSearch".equals(method.getName())) {
                com.intellij.psi.PsiParameterList parameterList = method.getParameterList();
                if (parameterList.getParametersCount() == 2) {
                    com.intellij.psi.PsiParameter[] parameters = parameterList.getParameters();
                    String type0 = parameters[0].getType().getCanonicalText();
                    String type1 = parameters[1].getType().getCanonicalText();
                    if (("java.lang.String".equals(type0) || "String".equals(type0)) && 
                        ("android.os.Bundle".equals(type1) || "Bundle".equals(type1))) {
                        overridesOnPlayFromSearch = true;
                        break;
                    }
                }
            }
        }

        if (overridesOnPlayFromSearch) {
            mHasOnPlayFromSearch = true;
        } else {
            mCallbackLocationsWithoutOnPlayFromSearch.add(context.getNameLocation(declaration));
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mHasIntentFilter && !mHasOnPlayFromSearch) {
            if (!mCallbackLocationsWithoutOnPlayFromSearch.isEmpty()) {
                for (Location location : mCallbackLocationsWithoutOnPlayFromSearch) {
                    context.report(
                            ISSUE,
                            location,
                            "This class extends `MediaSession.Callback` but does not override `onPlayFromSearch` even though the manifest registers a `MEDIA_PLAY_FROM_SEARCH` intent-filter"
                    );
                }
            } else if (mIntentFilterLocation != null) {
                context.report(
                        ISSUE,
                        mIntentFilterLocation,
                        "Missing `onPlayFromSearch` implementation in a `MediaSession.Callback` to support voice search"
                );
            }
        }
    }

    public void visitMethod() {
        // Unused as we perform class-level analysis in visitClass
    }
}
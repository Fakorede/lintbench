/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.ClassScanner;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
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
import java.util.EnumSet;
import java.util.List;

/**
 * Detector for Android Auto issues.
 * <p>
 * Checks that media browser services that declare support for voice searches
 * (via an intent-filter for ACTION_PLAY_FROM_SEARCH) also override
 * onPlayFromSearch in their MediaSession.Callback.
 */
public class AndroidAutoDetector extends Detector implements XmlScanner, ClassScanner {

    public static final Issue MISSING_ON_PLAY_FROM_SEARCH = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing `onPlayFromSearch`",
            "To support voice searches on Android Auto, in addition to adding an "
                    + "`intent-filter` for the action `onPlayFromSearch`, "
                    + "you also need to override and implement "
                    + "`onPlayFromSearch(String query, Bundle bundle)`",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.CLASS_FILE, Scope.ALL_CLASS_FILES)));

    private static final String ACTION_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    private static final String MEDIA_SESSION_CALLBACK_CLASS =
            "android/media/session/MediaSession$Callback";

    private static final String MEDIA_SESSION_COMPAT_CALLBACK_CLASS =
            "android/support/v4/media/session/MediaSessionCompat$Callback";

    private static final String ON_PLAY_FROM_SEARCH_METHOD = "onPlayFromSearch";

    private static final String ON_PLAY_FROM_SEARCH_DESCRIPTOR =
            "(Ljava/lang/String;Landroid/os/Bundle;)V";

    /** Whether the manifest contains the play-from-search intent filter */
    private boolean mHasPlayFromSearchIntentFilter;

    /** Whether we found an implementation of onPlayFromSearch */
    private boolean mHasOnPlayFromSearchImplementation;

    /** Location to report the error (from the manifest) */
    private Location mManifestLocation;

    public AndroidAutoDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("action");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android", "name");
        if (ACTION_PLAY_FROM_SEARCH.equals(name)) {
            mHasPlayFromSearchIntentFilter = true;
            mManifestLocation = context.getLocation(element);
        }
    }

    // ---- Implements ClassScanner ----

    @Override
    @Nullable
    public List<String> getApplicableSuperClasses() {
        return Arrays.asList(
                MEDIA_SESSION_CALLBACK_CLASS,
                MEDIA_SESSION_COMPAT_CALLBACK_CLASS);
    }

    @Override
    public void checkClass(@NonNull ClassContext context, @NonNull ClassNode classNode) {
        if (!mHasPlayFromSearchIntentFilter) {
            return;
        }

        // Check if this class overrides onPlayFromSearch
        @SuppressWarnings("unchecked")
        List<MethodNode> methods = classNode.methods;
        if (methods != null) {
            for (MethodNode method : methods) {
                if (ON_PLAY_FROM_SEARCH_METHOD.equals(method.name)
                        && ON_PLAY_FROM_SEARCH_DESCRIPTOR.equals(method.desc)) {
                    mHasOnPlayFromSearchImplementation = true;
                    return;
                }
            }
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (mHasPlayFromSearchIntentFilter && !mHasOnPlayFromSearchImplementation) {
            Location location = mManifestLocation;
            if (location == null) {
                location = Location.create(context.getProject().getDir());
            }
            context.report(
                    MISSING_ON_PLAY_FROM_SEARCH,
                    location,
                    "To support voice searches on Android Auto, in addition to adding an "
                            + "`intent-filter` for the action `onPlayFromSearch`, "
                            + "you also need to override and implement "
                            + "`onPlayFromSearch(String query, Bundle bundle)`");
        }
    }
}
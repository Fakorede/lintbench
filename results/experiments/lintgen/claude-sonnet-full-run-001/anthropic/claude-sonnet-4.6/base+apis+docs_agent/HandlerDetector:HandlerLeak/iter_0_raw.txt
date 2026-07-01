/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;

import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;

import java.util.Collections;
import java.util.List;

/**
 * Checks for potential Handler memory leaks caused by non-static inner Handler classes.
 */
public class HandlerDetector extends Detector implements SourceCodeScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "HandlerLeak",
            "Handler reference leaks",
            "Since this `Handler` is declared as an inner class, it may prevent the outer " +
            "class from being garbage collected. If the `Handler` is using a `Looper` or " +
            "`MessageQueue` for a thread other than the main thread, then there is no issue. " +
            "If the `Handler` is using the `Looper` or `MessageQueue` of the main thread, " +
            "you need to fix your `Handler` declaration, as follows: Declare the `Handler` " +
            "as a static class; In the outer class, instantiate a `WeakReference` to the " +
            "outer class and pass this object to your `Handler` when you instantiate the "
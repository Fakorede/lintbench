package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ManifestTypoDetector extends Detector implements XmlScanner {
    public static final Issue ISSUE = Issue.create(
            "ManifestTypo",
            "Typos in manifest tags",
            "This check looks through the manifest, and if it finds any tags that look like likely misspellings, they are flagged.",
            Category.CORRECTNESS,
            Severity.WARNING,
            new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final Set<String> VALID_TAGS = new HashSet<>(Arrays.asList(
            "manifest", "application", "activity", "service", "receiver", "provider",
            "uses-permission", "uses-permission-sdk-23", "uses-feature", "uses-sdk",
            "intent-filter", "action", "category", "data", "meta-data", "uses-library",
            "supports-screens", "compatible-screens", "screen", "permission",
            "permission-group", "permission-tree", "instrumentation", "profileable",
            "queries", "package", "intent", "overlay", "uses-native-library",
            "restrict-update", "adopt-permissions", "original-package", "protected-broadcast",
            "eat-comment", "static-library", "library", "feature-group", "applies-to",
            "uses-configuration", "uses-gl-texture", "supports-gl-texture", "supports-input"
    ));

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        if (!SdkConstants.ANDROID_MANIFEST_XML.equals(context.file.getName())) {
            return;
        }

        String tagName = element.getTagName();
        if (tagName.isEmpty() || tagName.indexOf(':') != -1) {
            return;
        }

        if (VALID_TAGS.contains(tagName)) {
            return;
        }

        String suggestion = findClosestMatch(tagName);
        if (suggestion != null) {
            String message = String.format("Suspicious tag name `%s`: did you mean `%s`?", tagName, suggestion);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }

    @Nullable
    private static String findClosestMatch(@NotNull String typo) {
        String bestMatch = null;
        int minDistance = Integer.MAX_VALUE;

        for (String valid : VALID_TAGS) {
            int dist = levenshteinDistance(typo, valid);
            if (dist <= 2 && dist < minDistance) {
                minDistance = dist;
                bestMatch = valid;
            }
        }
        return bestMatch;
    }

    private static int levenshteinDistance(@NotNull String s1, @NotNull String s2) {
        int len1 = s1.length();
        int len2 = s2.length();

        if (Math.abs(len1 - len2) > 2) {
            return 3;
        }

        int[] prev = new int[len2 + 1];
        int[] curr = new int[len2 + 1];

        for (int j = 0; j <= len2; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            curr[0] = i;
            for (int j = 1; j <= len2; j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }
        return prev[len2];
    }
}
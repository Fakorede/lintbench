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
            6,
            Severity.WARNING,
            new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final Set<String> VALID_TAGS = new HashSet<>(Arrays.asList(
            "manifest", "application", "activity", "service", "receiver", "provider",
            "uses-permission", "uses-sdk", "uses-feature", "supports-screens",
            "compatible-screens", "instrumentation", "permission", "permission-group",
            "permission-tree", "uses-library", "meta-data", "intent-filter", "action",
            "category", "data", "grant-uri-permission", "path-permission", "layout",
            "activity-alias", "uses-configuration", "queries", "package", "intent"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!SdkConstants.ANDROID_MANIFEST_XML.equals(context.file.getName())) {
            return;
        }

        String tag = element.getTagName();
        if (tag == null || tag.isEmpty() || tag.contains(".") || VALID_TAGS.contains(tag)) {
            return;
        }

        String suggestion = findClosestTag(tag);
        if (suggestion != null) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Suspicious tag name `" + tag + "`; did you mean `" + suggestion + "`?");
        }
    }

    private static String findClosestTag(String tag) {
        String bestMatch = null;
        int bestDistance = 3;
        for (String valid : VALID_TAGS) {
            int dist = levenshtein(tag, valid);
            if (dist > 0 && dist < bestDistance) {
                bestDistance = dist;
                bestMatch = valid;
            }
        }
        return bestMatch;
    }

    private static int levenshtein(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        int[] dp = new int[len2 + 1];
        for (int j = 0; j <= len2; j++) {
            dp[j] = j;
        }
        for (int i = 1; i <= len1; i++) {
            int prev = dp[0];
            dp[0] = i;
            for (int j = 1; j <= len2; j++) {
                int temp = dp[j];
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[j] = Math.min(Math.min(dp[j] + 1, dp[j - 1] + 1), prev + cost);
                prev = temp;
            }
        }
        return dp[len2];
    }
}
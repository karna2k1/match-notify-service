package com.jobradar.match.scoring;

import com.jobradar.match.profile.MatchingProfile.Profile;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Location / work-mode fit. "Remote" only counts when the job is open globally or in a preferred place. */
public final class LocationRules {
    static final Pattern GLOBAL_REMOTE = Pattern.compile(
            "(?i)^\\s*(fully\\s+)?(remote|anywhere|worldwide|global)\\s*$|\\b(worldwide|anywhere|apac|asia)\\b");
    private static final Map<String, String> ALIASES = Map.of(
            "bangalore", "bengaluru", "gurgaon", "gurugram", "bombay", "mumbai", "madras", "chennai",
            "trivandrum", "thiruvananthapuram", "new delhi", "delhi", "calcutta", "kolkata");
    private static final List<String> NOT_PLACES = List.of("remote", "anywhere", "worldwide", "global");

    public enum Fit { IN_PLACE, OPEN_REMOTE, NO_PREFERENCE, MISMATCH }

    private LocationRules() {}

    public static Fit fit(Profile p, IngestedJob job) {
        String loc = norm(job.location());
        List<String> places = p.locations().stream().map(LocationRules::norm)
                .filter(l -> !l.isBlank() && !NOT_PLACES.contains(l)).toList();
        boolean inPlace = !loc.isBlank() && places.stream().anyMatch(loc::contains);
        boolean openRemote = job.remote() && (loc.isBlank() || GLOBAL_REMOTE.matcher(loc).find() || inPlace);
        String mode = p.workMode() == null ? "ANY" : p.workMode();

        if (mode.equals("REMOTE")) return openRemote ? Fit.OPEN_REMOTE : Fit.MISMATCH;
        if (inPlace) return Fit.IN_PLACE;
        if (openRemote && !mode.equals("ONSITE")) return Fit.OPEN_REMOTE;
        return places.isEmpty() ? Fit.NO_PREFERENCE : Fit.MISMATCH;
    }

    static String norm(String s) {
        if (s == null) return "";
        String v = s.toLowerCase(Locale.ROOT).strip();
        for (var e : ALIASES.entrySet()) v = v.replace(e.getKey(), e.getValue());
        return v;
    }
}

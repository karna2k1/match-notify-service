package com.jobradar.match.scoring;

import static com.jobradar.match.scoring.LocationRules.Fit.IN_PLACE;
import static com.jobradar.match.scoring.LocationRules.Fit.MISMATCH;
import static com.jobradar.match.scoring.LocationRules.Fit.NO_PREFERENCE;
import static com.jobradar.match.scoring.LocationRules.Fit.OPEN_REMOTE;
import static org.assertj.core.api.Assertions.assertThat;

import com.jobradar.match.profile.MatchingProfile.Profile;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocationRulesTest {
    private static Profile profile(String mode, String... locations) {
        return new Profile(null, null, null, null, null, mode, List.of("Java"), List.of(), List.of(locations),
                List.of(), 70, null, null);
    }

    private static IngestedJob job(String location, boolean remote) {
        return new IngestedJob(1L, "X", "Acme", "Java Developer", location, remote, "u", null, null, null);
    }

    @Test
    void indiaProfileRejectsAbroad() {
        var p = profile("ANY", "Bengaluru", "Hyderabad", "India", "Remote");
        assertThat(LocationRules.fit(p, job("Bangalore, Karnataka", false))).isEqualTo(IN_PLACE);
        assertThat(LocationRules.fit(p, job("Pune, India", false))).isEqualTo(IN_PLACE);
        assertThat(LocationRules.fit(p, job("Worldwide", true))).isEqualTo(OPEN_REMOTE);
        assertThat(LocationRules.fit(p, job("New York, NY", false))).isEqualTo(MISMATCH);
        assertThat(LocationRules.fit(p, job("US-Remote", true))).isEqualTo(MISMATCH);
    }

    @Test
    void workModes() {
        assertThat(LocationRules.fit(profile("REMOTE", "India"), job("Bengaluru, India", false))).isEqualTo(MISMATCH);
        assertThat(LocationRules.fit(profile("ONSITE", "Pune"), job("Remote", true))).isEqualTo(MISMATCH);
        assertThat(LocationRules.fit(profile("ANY"), job("Berlin", false))).isEqualTo(NO_PREFERENCE);
    }
}

package com.jobradar.match.scoring;

import java.time.Instant;

/** Payload of job.ingested (published by job-ingest-service). */
public record IngestedJob(Long id, String source, String company, String title, String location, boolean remote,
                          String url, String description, String salary, Instant postedAt) {

    public String text() {
        return title + "\n" + (company == null ? "" : company) + "\n" + (location == null ? "" : location)
                + "\n" + (description == null ? "" : description);
    }
}

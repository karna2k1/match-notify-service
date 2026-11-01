package com.jobradar.match.match;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "matches")
@Getter
@Setter
@NoArgsConstructor
public class Match {
    public enum Status { NEW, SAVED, IGNORED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(length = 40)
    private String source;

    @Column(nullable = false, length = 200)
    private String company;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 300)
    private String location;

    @Column(nullable = false)
    private boolean remote;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(name = "keyword_score", nullable = false)
    private int keywordScore;

    @Column(name = "embedding_score")
    private Integer embeddingScore;

    @Column(name = "llm_score")
    private Integer llmScore;

    @Column(name = "final_score", nullable = false)
    private int finalScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> reasons = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matched_skills", nullable = false, columnDefinition = "jsonb")
    private List<String> matchedSkills = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skill_gaps", nullable = false, columnDefinition = "jsonb")
    private List<String> skillGaps = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.NEW;

    @Column(name = "instant_sent", nullable = false)
    private boolean instantSent;

    @Column(name = "digest_sent", nullable = false)
    private boolean digestSent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

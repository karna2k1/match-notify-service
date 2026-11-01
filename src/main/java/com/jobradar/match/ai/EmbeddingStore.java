package com.jobradar.match.ai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** pgvector persistence and cosine similarity (1 - cosine distance via the <=> operator). */
@Component
@RequiredArgsConstructor
public class EmbeddingStore {
    private final JdbcTemplate jdbc;

    public Optional<String> profileHash(Long userId) {
        return jdbc.query("select profile_hash from profile_embeddings where user_id = ?",
                rs -> rs.next() ? Optional.of(rs.getString(1)) : Optional.<String>empty(), userId);
    }

    public void saveProfile(Long userId, String hash, float[] v) {
        jdbc.update("""
                insert into profile_embeddings(user_id, profile_hash, embedding) values (?, ?, cast(? as vector))
                on conflict (user_id) do update set profile_hash = excluded.profile_hash,
                  embedding = excluded.embedding, updated_at = now()
                """, userId, hash, literal(v));
    }

    public void saveJob(Long jobId, float[] v) {
        jdbc.update("insert into job_embeddings(job_id, embedding) values (?, cast(? as vector)) on conflict (job_id) do nothing",
                jobId, literal(v));
    }

    public boolean hasJob(Long jobId) {
        Integer n = jdbc.queryForObject("select count(*) from job_embeddings where job_id = ?", Integer.class, jobId);
        return n != null && n > 0;
    }

    /** Cosine similarity between one profile and several jobs. */
    public Map<Long, Double> similarities(Long userId, List<Long> jobIds) {
        Map<Long, Double> out = new HashMap<>();
        if (jobIds.isEmpty()) return out;
        String in = String.join(",", jobIds.stream().map(String::valueOf).toList());
        jdbc.query("select j.job_id, 1 - (j.embedding <=> p.embedding) from job_embeddings j, profile_embeddings p "
                        + "where p.user_id = ? and j.job_id in (" + in + ")",
                rs -> { out.put(rs.getLong(1), rs.getDouble(2)); }, userId);
        return out;
    }

    static String literal(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}

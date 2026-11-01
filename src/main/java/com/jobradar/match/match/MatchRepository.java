package com.jobradar.match.match;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchRepository extends JpaRepository<Match, Long> {
    boolean existsByUserIdAndJobId(Long userId, Long jobId);

    Optional<Match> findByIdAndUserId(Long id, Long userId);

    @Query("""
            select m from Match m
            where m.userId = :userId and m.finalScore >= :minScore
              and (:status = '' or cast(m.status as string) = :status)
              and (:q = '' or lower(m.title) like lower(concat('%', :q, '%'))
                   or lower(m.company) like lower(concat('%', :q, '%')))
            order by m.createdAt desc, m.finalScore desc
            """)
    Page<Match> search(@Param("userId") Long userId, @Param("minScore") int minScore,
                       @Param("status") String status, @Param("q") String q, Pageable pageable);

    /** Feedback loop: companies this user ignored at least twice get a score penalty. */
    @Query("""
            select lower(m.company) from Match m
            where m.userId = :userId and m.status = com.jobradar.match.match.Match.Status.IGNORED
            group by lower(m.company) having count(m) >= 2
            """)
    List<String> repeatedlyIgnoredCompanies(@Param("userId") Long userId);

    @Query("""
            select m from Match m
            where m.digestSent = false and m.instantSent = false and m.createdAt >= :since
              and m.status <> com.jobradar.match.match.Match.Status.IGNORED
            order by m.userId, m.finalScore desc
            """)
    List<Match> pendingDigest(@Param("since") Instant since);
}

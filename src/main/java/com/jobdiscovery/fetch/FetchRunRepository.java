package com.jobdiscovery.fetch;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link FetchRun}. */
public interface FetchRunRepository extends JpaRepository<FetchRun, Long> {

    /** The most recent attempt, whatever triggered it. Drives the startup guard. */
    Optional<FetchRun> findFirstByOrderByRanAtDesc();

    /** Recent runs, newest first — the answer to "has the scheduler fired?". */
    List<FetchRun> findTop20ByOrderByRanAtDesc();
}

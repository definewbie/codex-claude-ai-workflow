package app.cryptoincome.shadow;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GridShadowStudyRepository {
    long create(GridShadowStudy study);
    Optional<GridShadowStudy> find(long id);
    List<GridShadowStudy> findAll();
    List<GridShadowDailyFact> findFacts(long studyId);
    void appendFacts(List<GridShadowDailyFact> facts);
    boolean stop(long id, Instant stoppedAt);
}

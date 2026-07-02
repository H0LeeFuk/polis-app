package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Locks in the build-queue invariants that a gold-rush must never break — the class of bug where
 * rushing an upgrade left a phantom slot that "never disappears" or showed two running countdowns.
 *
 * The JobRepo + BuildingRepo are backed by in-memory lists so delete→flush→re-read behaves like the
 * real DB (which is exactly what {@link CityService#forceComplete} and {@link CityService#finalizeJobs}
 * rely on). After every queue mutation we assert the three invariants the UI depends on:
 *   (1) no duplicate job ids, (2) positions are contiguous 0..n-1, (3) at most ONE job is running
 *       (finishAt set) and only ever the head.
 */
class BuildQueueInvariantTest {
  private static final long CITY = 42L, PLAYER = 7L;

  private final List<BuildJob> jobStore = new ArrayList<>();
  private final List<CityBuilding> bldStore = new ArrayList<>();
  private final AtomicLong jobSeq = new AtomicLong(100);

  private CityService svc;
  private City city;

  @BeforeEach
  void setUp() {
    CityRepo cities = mock(CityRepo.class);
    BuildingRepo buildings = mock(BuildingRepo.class);
    UnitRepo units = mock(UnitRepo.class);
    JobRepo jobs = mock(JobRepo.class);
    MissionService missions = mock(MissionService.class);
    LibraryService library = mock(LibraryService.class);
    MovementRepo movements = mock(MovementRepo.class);
    PlayerRepo players = mock(PlayerRepo.class);
    UnitCatalog catalog = mock(UnitCatalog.class);

    city = new City();
    city.setId(CITY);
    city.setPlayerId(PLAYER);

    when(cities.findByIdForUpdate(CITY)).thenReturn(Optional.of(city));

    // ---- in-memory JobRepo ----
    when(jobs.findByCityIdAndQueueTypeOrderByPositionAsc(eq(CITY), any(QueueType.class))).thenAnswer(inv -> {
      QueueType qt = inv.getArgument(1);
      List<BuildJob> out = new ArrayList<>();
      for (BuildJob j : jobStore) if (Objects.equals(j.getCityId(), CITY) && j.getQueueType() == qt) out.add(j);
      out.sort(Comparator.comparingInt(BuildJob::getPosition));
      return out;   // fresh list, like a DB query
    });
    doAnswer(inv -> { BuildJob j = inv.getArgument(0); jobStore.removeIf(x -> Objects.equals(x.getId(), j.getId())); return null; })
      .when(jobs).delete(any(BuildJob.class));
    when(jobs.save(any(BuildJob.class))).thenAnswer(inv -> upsertJob(inv.getArgument(0)));
    when(jobs.saveAll(any())).thenAnswer(inv -> {
      List<BuildJob> saved = new ArrayList<>();
      for (BuildJob j : (Iterable<BuildJob>) inv.getArgument(0)) saved.add(upsertJob(j));
      return saved;
    });

    // ---- in-memory BuildingRepo (for applyJob) ----
    when(buildings.findByCityId(CITY)).thenAnswer(inv -> new ArrayList<>(bldStore));
    when(buildings.save(any(CityBuilding.class))).thenAnswer(inv -> {
      CityBuilding b = inv.getArgument(0);
      bldStore.removeIf(x -> x.getType() == b.getType());
      bldStore.add(b);
      return b;
    });

    svc = new CityService(cities, buildings, units, jobs, catalog, missions, library, movements, players);
  }

  private BuildJob upsertJob(BuildJob j) {
    if (j.getId() == null) j.setId(jobSeq.incrementAndGet());
    jobStore.removeIf(x -> Objects.equals(x.getId(), j.getId()));
    jobStore.add(j);
    return j;
  }

  /** Seed a BUILDING queue of upgrades; head runs now→+dur, the rest idle (as enqueue would leave them). */
  private void seedBuilding(String... typesToLevel) {
    Instant now = Instant.now();
    int pos = 0;
    for (String spec : typesToLevel) {
      String[] parts = spec.split(":");
      BuildingType type = BuildingType.valueOf(parts[0]);
      int toLevel = Integer.parseInt(parts[1]);
      BuildJob j = new BuildJob();
      j.setId(jobSeq.incrementAndGet());
      j.setCityId(CITY);
      j.setQueueType(QueueType.BUILDING);
      j.setBuildingType(type);
      j.setToLevel(toLevel);
      j.setPosition(pos);
      j.setTotalSeconds(600);
      if (pos == 0) { j.setStartedAt(now); j.setFinishAt(now.plusSeconds(600)); }
      jobStore.add(j);
      pos++;
    }
  }

  private List<BuildJob> queue() {
    List<BuildJob> q = new ArrayList<>();
    for (BuildJob j : jobStore) if (j.getQueueType() == QueueType.BUILDING) q.add(j);
    q.sort(Comparator.comparingInt(BuildJob::getPosition));
    return q;
  }

  /** The three invariants the UI relies on — asserted after every mutation. */
  private void assertInvariants() {
    List<BuildJob> q = queue();
    // (1) no duplicate ids
    assertThat(q.stream().map(BuildJob::getId).distinct().count()).as("no duplicate job ids").isEqualTo(q.size());
    // (2) contiguous positions 0..n-1
    for (int i = 0; i < q.size(); i++)
      assertThat(q.get(i).getPosition()).as("contiguous position at index " + i).isEqualTo(i);
    // (3) at most one running, and only the head
    long running = q.stream().filter(j -> j.getFinishAt() != null).count();
    assertThat(running).as("at most one running slot").isLessThanOrEqualTo(1);
    if (!q.isEmpty()) {
      assertThat(q.get(0).getFinishAt()).as("head is the running slot").isNotNull();
      for (int i = 1; i < q.size(); i++)
        assertThat(q.get(i).getFinishAt()).as("non-head slot " + i + " is idle").isNull();
    }
  }

  @Test
  void rushHead_dropsExactlyOneJob_promotesNext_singleRunner() {
    seedBuilding("FARM:3", "QUARRY:2", "FARM:4");
    BuildJob head = queue().get(0);

    svc.forceComplete(city, head);

    assertThat(queue()).as("exactly one job removed").hasSize(2);
    assertThat(queue()).extracting(BuildJob::getId).doesNotContain(head.getId());
    assertThat(bldStore).anySatisfy(b -> { assertThat(b.getType()).isEqualTo(BuildingType.FARM); assertThat(b.getLevel()).isEqualTo(3); });
    assertThat(queue().get(0).getBuildingType()).isEqualTo(BuildingType.QUARRY);   // promoted
    assertInvariants();
  }

  @Test
  void rushMidQueueJob_dropsOnlyThatJob_keepsHeadRunning() {
    seedBuilding("FARM:3", "QUARRY:2", "MINE:5");
    BuildJob mid = queue().get(1);   // QUARRY

    svc.forceComplete(city, mid);

    assertThat(queue()).hasSize(2);
    assertThat(queue()).extracting(BuildJob::getBuildingType)
        .containsExactly(BuildingType.FARM, BuildingType.MINE);
    assertInvariants();
  }

  @Test
  void rushLastRemainingJob_leavesEmptyQueue() {
    seedBuilding("FARM:3");
    svc.forceComplete(city, queue().get(0));
    assertThat(queue()).isEmpty();
    assertInvariants();   // empty queue is valid (no running slot)
  }

  @Test
  void rushEveryJobSequentially_neverLeavesPhantom() {
    seedBuilding("FARM:3", "QUARRY:2", "FARM:4", "MINE:5");
    int expected = 4;
    while (!queue().isEmpty()) {
      svc.forceComplete(city, queue().get(0));
      expected--;
      assertThat(queue()).as("count decreases by exactly one each rush").hasSize(expected);
      assertInvariants();
    }
    assertThat(queue()).isEmpty();
  }

  @Test
  void finalizeThenForceComplete_mirrorsFinishWithGold_noPhantom() {
    // finishWithGold does finalizeJobs(now) THEN forceComplete(job) in one tx — replay that here
    // with the head NOT yet due, so finalize is a no-op and the rush drops exactly one job.
    seedBuilding("FARM:3", "QUARRY:2");
    svc.finalizeJobs(city, Instant.now());
    BuildJob head = queue().get(0);
    svc.forceComplete(city, head);
    assertThat(queue()).hasSize(1);
    assertThat(queue().get(0).getBuildingType()).isEqualTo(BuildingType.QUARRY);
    assertInvariants();
  }

  @Test
  void finalizeJobs_completesDueHead_andPromotesWithFreshTimer() {
    seedBuilding("FARM:3", "QUARRY:2");
    // force the head's timer into the past
    BuildJob head = queue().get(0);
    head.setFinishAt(Instant.now().minusSeconds(5));

    svc.finalizeJobs(city, Instant.now());

    assertThat(queue()).hasSize(1);
    BuildJob promoted = queue().get(0);
    assertThat(promoted.getBuildingType()).isEqualTo(BuildingType.QUARRY);
    assertThat(promoted.getFinishAt()).as("promoted head starts running").isNotNull();
    assertThat(bldStore).anySatisfy(b -> { assertThat(b.getType()).isEqualTo(BuildingType.FARM); assertThat(b.getLevel()).isEqualTo(3); });
    assertInvariants();
  }

  @Test
  void normalize_healsCorruptedQueue_multipleRunners_andBadPositions() {
    // Simulate a corrupted persisted state: two "running" jobs and non-contiguous positions.
    Instant now = Instant.now();
    BuildJob a = new BuildJob(); a.setId(1L); a.setCityId(CITY); a.setQueueType(QueueType.BUILDING);
    a.setBuildingType(BuildingType.FARM); a.setToLevel(3); a.setPosition(0); a.setTotalSeconds(600);
    a.setStartedAt(now); a.setFinishAt(now.plusSeconds(600));
    BuildJob b = new BuildJob(); b.setId(2L); b.setCityId(CITY); b.setQueueType(QueueType.BUILDING);
    b.setBuildingType(BuildingType.QUARRY); b.setToLevel(2); b.setPosition(5); b.setTotalSeconds(600);
    b.setStartedAt(now); b.setFinishAt(now.plusSeconds(600));   // illegal: a second runner
    jobStore.add(a); jobStore.add(b);

    // a read/sync runs finalizeJobs, which normalizes the queue back to the invariant
    svc.finalizeJobs(city, Instant.now());

    assertInvariants();
    assertThat(queue()).hasSize(2);
  }
}

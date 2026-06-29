package com.polis.repo;

import com.polis.domain.BattleOutcome;
import com.polis.domain.BattleReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BattleReportRepo extends JpaRepository<BattleReport, Long> {

  /** Reports the player is a party to, excluding the ones they soft-deleted, newest first. */
  @Query("""
      select r from BattleReport r
      where ((r.attackerPlayerId = :me and r.attackerDeleted = false)
          or (r.defenderPlayerId = :me and r.defenderDeleted = false))
        and (:outcome is null or r.outcome = :outcome)
        and (:cityId is null or r.attackerCityId = :cityId or r.defenderCityId = :cityId)
        and (:role is null
             or (:role = 'ATTACKER' and r.attackerPlayerId = :me)
             or (:role = 'DEFENDER' and r.defenderPlayerId = :me))
        and (:read is null or (case when r.attackerPlayerId = :me then r.attackerRead else r.defenderRead end) = :read)
      order by r.foughtAt desc, r.id desc
      """)
  Page<BattleReport> search(@Param("me") Long me, @Param("outcome") BattleOutcome outcome,
                            @Param("cityId") Long cityId, @Param("role") String role,
                            @Param("read") Boolean read, Pageable pageable);

  @Query("""
      select count(r) from BattleReport r
      where (r.attackerPlayerId = :me and r.attackerDeleted = false and r.attackerRead = false)
         or (r.defenderPlayerId = :me and r.defenderDeleted = false and r.defenderRead = false)
      """)
  long countUnread(@Param("me") Long me);

  /** Anti-farming: how many Combat-Point-earning wins this winner already scored vs this loser recently. */
  @Query("""
      select count(r) from BattleReport r
      where r.attackerPlayerId = :winner and r.defenderPlayerId = :loser
        and r.combatPointsEarned > 0 and r.foughtAt > :since
      """)
  long countRecentWins(@Param("winner") Long winner, @Param("loser") Long loser,
                       @Param("since") java.time.Instant since);

  @Modifying
  @Query("update BattleReport r set r.attackerRead = true where r.attackerPlayerId = :me and r.attackerRead = false and r.attackerDeleted = false")
  void markAllReadAsAttacker(@Param("me") Long me);

  @Modifying
  @Query("update BattleReport r set r.defenderRead = true where r.defenderPlayerId = :me and r.defenderRead = false and r.defenderDeleted = false")
  void markAllReadAsDefender(@Param("me") Long me);
}

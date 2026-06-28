package com.polis.game;

import com.polis.domain.Alliance;
import com.polis.domain.Player;
import com.polis.repo.AllianceRepo;
import com.polis.repo.PlayerRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/** Player-founded alliances. The founder becomes the leader and first member. */
@Service
public class AllianceService {
  private final AllianceRepo alliances;
  private final PlayerRepo players;

  public AllianceService(AllianceRepo alliances, PlayerRepo players){
    this.alliances = alliances; this.players = players;
  }

  @Transactional
  public Map<String,Object> create(Long playerId, String tag, String name){
    Player p = players.findById(playerId).orElseThrow(() -> new IllegalArgumentException("Player not found"));
    if (p.getAllianceId() != null) throw new IllegalStateException("You are already in an alliance");

    String t = tag == null ? "" : tag.trim();
    String n = name == null ? "" : name.trim();
    if (t.length() < 2 || t.length() > 6) throw new IllegalStateException("Tag must be 2–6 characters");
    if (n.length() < 3 || n.length() > 32) throw new IllegalStateException("Name must be 3–32 characters");
    if (alliances.existsByWorldIdAndTagIgnoreCase(p.getWorldId(), t)) throw new IllegalStateException("Tag already taken");
    if (alliances.existsByWorldIdAndNameIgnoreCase(p.getWorldId(), n)) throw new IllegalStateException("Name already taken");

    Alliance a = new Alliance();
    a.setWorldId(p.getWorldId()); a.setTag(t); a.setName(n); a.setLeaderId(playerId);
    a = alliances.save(a);

    p.setAllianceId(a.getId()); players.save(p);

    Map<String,Object> out = new LinkedHashMap<>();
    out.put("id", a.getId()); out.put("tag", a.getTag()); out.put("name", a.getName());
    return out;
  }

  @Transactional
  public void leave(Long playerId){
    Player p = players.findById(playerId).orElseThrow(() -> new IllegalArgumentException("Player not found"));
    if (p.getAllianceId() == null) throw new IllegalStateException("You are not in an alliance");
    p.setAllianceId(null); players.save(p);
  }
}

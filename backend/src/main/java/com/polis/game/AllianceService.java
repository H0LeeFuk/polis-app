package com.polis.game;

import com.polis.domain.Alliance;
import com.polis.domain.AllianceForumPost;
import com.polis.domain.AllianceInvite;
import com.polis.domain.Player;
import com.polis.repo.AllianceForumPostRepo;
import com.polis.repo.AllianceInviteRepo;
import com.polis.repo.AllianceRepo;
import com.polis.repo.PlayerRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Player-founded alliances: membership, invitations, and a simple forum. */
@Service
public class AllianceService {
  private final AllianceRepo alliances;
  private final PlayerRepo players;
  private final AllianceInviteRepo invites;
  private final AllianceForumPostRepo forum;

  public AllianceService(AllianceRepo alliances, PlayerRepo players,
                         AllianceInviteRepo invites, AllianceForumPostRepo forum){
    this.alliances = alliances; this.players = players; this.invites = invites; this.forum = forum;
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
    invites.deleteByPlayerId(playerId);   // clear any pending invites once they've founded one

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

  /** Full alliance view for a player: membership + members + forum, or pending invites if unaffiliated. */
  @Transactional(readOnly = true)
  public Map<String,Object> me(Long playerId){
    Player p = players.findById(playerId).orElseThrow(() -> new IllegalArgumentException("Player not found"));
    Map<String,Object> out = new LinkedHashMap<>();
    if (p.getAllianceId() == null){
      out.put("inAlliance", false);
      List<Map<String,Object>> pending = new ArrayList<>();
      for (AllianceInvite inv : invites.findByPlayerId(playerId)){
        Alliance a = alliances.findById(inv.getAllianceId()).orElse(null);
        if (a == null) continue;
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("allianceId", a.getId()); m.put("tag", a.getTag()); m.put("name", a.getName());
        m.put("invitedBy", inv.getInvitedBy()==null ? null :
            players.findById(inv.getInvitedBy()).map(Player::getUsername).orElse(null));
        pending.add(m);
      }
      out.put("invites", pending);
      return out;
    }
    Alliance a = alliances.findById(p.getAllianceId()).orElseThrow();
    boolean leader = Objects.equals(a.getLeaderId(), playerId);
    out.put("inAlliance", true);
    out.put("id", a.getId()); out.put("tag", a.getTag()); out.put("name", a.getName());
    out.put("isLeader", leader);

    List<Map<String,Object>> members = new ArrayList<>();
    for (Player m : players.findByAllianceId(a.getId())){
      Map<String,Object> mm = new LinkedHashMap<>();
      mm.put("id", m.getId()); mm.put("name", m.getUsername()); mm.put("level", m.getLevel());
      mm.put("leader", Objects.equals(a.getLeaderId(), m.getId()));
      members.add(mm);
    }
    members.sort((x,y) -> Boolean.compare((boolean)y.get("leader"), (boolean)x.get("leader")));
    out.put("members", members);

    if (leader){
      List<Map<String,Object>> pend = new ArrayList<>();
      for (AllianceInvite inv : invites.findByAllianceId(a.getId())){
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("name", players.findById(inv.getPlayerId()).map(Player::getUsername).orElse("?"));
        pend.add(m);
      }
      out.put("pendingInvites", pend);
    }

    List<Map<String,Object>> posts = new ArrayList<>();
    for (AllianceForumPost fp : forum.findTop50ByAllianceIdOrderByCreatedAtDesc(a.getId())){
      Map<String,Object> m = new LinkedHashMap<>();
      m.put("id", fp.getId());
      m.put("author", players.findById(fp.getAuthorPlayerId()).map(Player::getUsername).orElse("?"));
      m.put("body", fp.getBody()); m.put("at", fp.getCreatedAt().toString());
      posts.add(m);
    }
    out.put("forum", posts);
    return out;
  }

  /** Leader invites a player (by username) in the same world to join. */
  @Transactional
  public void invite(Long playerId, String username){
    Player p = players.findById(playerId).orElseThrow(() -> new IllegalArgumentException("Player not found"));
    if (p.getAllianceId() == null) throw new IllegalStateException("You are not in an alliance");
    Alliance a = alliances.findById(p.getAllianceId()).orElseThrow();
    if (!Objects.equals(a.getLeaderId(), playerId)) throw new IllegalStateException("Only the leader can invite");
    Player target = players.findByUsernameIgnoreCase(username == null ? "" : username.trim())
        .orElseThrow(() -> new IllegalStateException("No player named \"" + username + "\""));
    if (!Objects.equals(target.getWorldId(), p.getWorldId())) throw new IllegalStateException("Player is in another world");
    if (Objects.equals(target.getId(), playerId)) throw new IllegalStateException("You're already in this alliance");
    if (target.getAllianceId() != null) throw new IllegalStateException(username + " is already in an alliance");
    if (invites.existsByAllianceIdAndPlayerId(a.getId(), target.getId())) throw new IllegalStateException(username + " is already invited");
    AllianceInvite inv = new AllianceInvite();
    inv.setAllianceId(a.getId()); inv.setPlayerId(target.getId()); inv.setInvitedBy(playerId);
    invites.save(inv);
  }

  @Transactional
  public void acceptInvite(Long playerId, Long allianceId){
    Player p = players.findById(playerId).orElseThrow(() -> new IllegalArgumentException("Player not found"));
    if (p.getAllianceId() != null) throw new IllegalStateException("You are already in an alliance");
    invites.findByAllianceIdAndPlayerId(allianceId, playerId)
        .orElseThrow(() -> new IllegalStateException("No such invitation"));
    alliances.findById(allianceId).orElseThrow(() -> new IllegalStateException("Alliance no longer exists"));
    p.setAllianceId(allianceId); players.save(p);
    invites.deleteByPlayerId(playerId);   // drop all other pending invites
  }

  @Transactional
  public void declineInvite(Long playerId, Long allianceId){
    invites.findByAllianceIdAndPlayerId(allianceId, playerId).ifPresent(invites::delete);
  }

  @Transactional
  public void post(Long playerId, String body){
    Player p = players.findById(playerId).orElseThrow(() -> new IllegalArgumentException("Player not found"));
    if (p.getAllianceId() == null) throw new IllegalStateException("You are not in an alliance");
    String b = body == null ? "" : body.trim();
    if (b.isEmpty()) throw new IllegalStateException("Message is empty");
    if (b.length() > 2000) b = b.substring(0, 2000);
    AllianceForumPost fp = new AllianceForumPost();
    fp.setAllianceId(p.getAllianceId()); fp.setAuthorPlayerId(playerId); fp.setBody(b);
    forum.save(fp);
  }
}

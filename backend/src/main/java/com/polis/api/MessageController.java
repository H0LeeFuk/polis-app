package com.polis.api;

import com.polis.config.SecurityConfig;
import com.polis.domain.Message;
import com.polis.domain.Player;
import com.polis.repo.MessageRepo;
import com.polis.repo.PlayerRepo;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
  private final MessageRepo messages; private final PlayerRepo players;
  public MessageController(MessageRepo messages, PlayerRepo players){ this.messages=messages; this.players=players; }

  public record SendRequest(Long toPlayerId, String body){}

  @GetMapping
  public List<Map<String,Object>> inbox(){
    Long me = SecurityConfig.currentPlayerId();
    Map<Long,String> names = new HashMap<>();
    players.findAll().forEach(p->names.put(p.getId(), p.getUsername()));
    List<Map<String,Object>> out = new ArrayList<>();
    for (Message m : messages.findByToPlayerIdOrderBySentAtDesc(me)){
      Map<String,Object> x = new LinkedHashMap<>();
      x.put("id", m.getId());
      x.put("from", names.getOrDefault(m.getFromPlayerId(), "Unknown"));
      x.put("body", m.getBody());
      x.put("sentAt", m.getSentAt().toString());
      x.put("read", m.isRead());
      out.add(x);
    }
    return out;
  }

  @PostMapping
  public Map<String,Object> send(@RequestBody SendRequest r){
    Long me = SecurityConfig.currentPlayerId();
    if (r.body()==null || r.body().isBlank()) throw new IllegalArgumentException("Message is empty");
    Player to = players.findById(r.toPlayerId()).orElseThrow(() -> new IllegalArgumentException("Recipient not found"));
    Message m = new Message();
    m.setFromPlayerId(me); m.setToPlayerId(to.getId());
    m.setBody(r.body().length()>1000 ? r.body().substring(0,1000) : r.body());
    messages.save(m);
    return Map.of("ok", true);
  }
}

package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="messages")
@Getter @Setter @NoArgsConstructor
public class Message {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="from_player_id") private Long fromPlayerId;
  @Column(name="to_player_id") private Long toPlayerId;
  @Column(length=1000, nullable=false) private String body;
  @Column(name="sent_at") private Instant sentAt = Instant.now();
  @Column(name="read_flag") private boolean read = false;
}

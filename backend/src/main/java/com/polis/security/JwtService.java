package com.polis.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
  private final SecretKey key;
  private final long ttlHours;

  public JwtService(@Value("${polis.jwt.secret}") String secret,
                    @Value("${polis.jwt.ttl-hours}") long ttlHours){
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.ttlHours = ttlHours;
  }

  public String issue(Long playerId, String username){
    Instant now = Instant.now();
    return Jwts.builder()
      .subject(String.valueOf(playerId))
      .claim("u", username)
      .issuedAt(Date.from(now))
      .expiration(Date.from(now.plusSeconds(ttlHours*3600)))
      .signWith(key)
      .compact();
  }

  /** @return playerId, or null if invalid/expired. */
  public Long verify(String token){
    try {
      var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
      return Long.valueOf(claims.getSubject());
    } catch (Exception e){ return null; }
  }
}

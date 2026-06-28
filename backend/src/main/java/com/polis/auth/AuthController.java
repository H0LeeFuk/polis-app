package com.polis.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService auth;
  public AuthController(AuthService auth){ this.auth = auth; }

  public record RegisterRequest(@NotBlank @Size(min=3,max=32) String username,
                                String email,
                                @NotBlank @Size(min=6,max=72) String password){}
  public record LoginRequest(@NotBlank String username, @NotBlank String password){}

  @PostMapping("/register")
  public Map<String,String> register(@Valid @RequestBody RegisterRequest r){
    return Map.of("token", auth.register(r.username().trim(), r.email(), r.password()));
  }

  @PostMapping("/login")
  public Map<String,String> login(@Valid @RequestBody LoginRequest r){
    return Map.of("token", auth.login(r.username().trim(), r.password()));
  }
}

package uk.ac.ebi.biostudies.index_service.auth;

import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@Setter
@Getter
public class User {
  private String login;
  private String fullName;
  private String token;
  private String[] allow;
  private String[] deny;
  private String email;
  private boolean superUser;

  public Set<GrantedAuthority> getAuthorities() {
    Set<GrantedAuthority> grantedAuths = new HashSet<>();
    if (allow != null) for (String s : allow) grantedAuths.add(new SimpleGrantedAuthority(s));
    if (superUser) grantedAuths.add(new SimpleGrantedAuthority("SUPER_USER"));
    return grantedAuths;
  }
}

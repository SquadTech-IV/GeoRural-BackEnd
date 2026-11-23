package br.com.fatec.georural.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter defaultConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>(defaultConverter.convert(jwt));

        Object realmAccessObj = jwt.getClaims().get("realm_access");
        if (realmAccessObj instanceof Map<?, ?> realmAccess) {
            Object rolesObj = realmAccess.get("roles");
            if (rolesObj instanceof Collection<?> roles) {
                for (Object role : roles) {
                    if (role != null) {
                        String nome = role.toString().trim().toUpperCase();
                        if (!nome.isEmpty()) {
                            authorities.add(new SimpleGrantedAuthority(
                                    nome.startsWith("ROLE_") ? nome : "ROLE_" + nome
                            ));
                        }
                    }
                }
            }
        }

        String principalName = jwt.getSubject() != null ? jwt.getSubject() : jwt.getClaimAsString("preferred_username");
        return new JwtAuthenticationToken(jwt, authorities, principalName);
    }
}
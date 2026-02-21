package com.nextgenmanager.nextgenmanager.common.service;

import com.nextgenmanager.nextgenmanager.common.model.AppUser;
import com.nextgenmanager.nextgenmanager.common.model.RefreshToken;
import com.nextgenmanager.nextgenmanager.common.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService service;

    @Test
    void issueToken_persistsActiveToken() {
        AppUser user = new AppUser();
        user.setId(1L);
        Date expiry = new Date(System.currentTimeMillis() + 60000);

        service.issueToken(user, "token-1", expiry, "system");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getToken()).isEqualTo("token-1");
        assertThat(captor.getValue().getRevoked()).isFalse();
        assertThat(captor.getValue().getAppUser().getId()).isEqualTo(1L);
    }

    @Test
    void revokeToken_marksTokenRevoked() {
        RefreshToken token = new RefreshToken();
        token.setToken("token-2");
        token.setRevoked(false);

        when(refreshTokenRepository.findByTokenAndRevokedFalseAndDeletedDateIsNull("token-2"))
                .thenReturn(Optional.of(token));

        service.revokeToken("token-2", "admin");

        verify(refreshTokenRepository).save(token);
        assertThat(token.getRevoked()).isTrue();
        assertThat(token.getRevokedDate()).isNotNull();
    }

    @Test
    void revokeAllForUser_revokesAllActiveTokens() {
        RefreshToken t1 = new RefreshToken();
        t1.setRevoked(false);
        RefreshToken t2 = new RefreshToken();
        t2.setRevoked(false);

        when(refreshTokenRepository.findByAppUser_IdAndRevokedFalseAndDeletedDateIsNull(5L))
                .thenReturn(List.of(t1, t2));

        int revokedCount = service.revokeAllForUser(5L, "admin");

        verify(refreshTokenRepository).saveAll(List.of(t1, t2));
        assertThat(revokedCount).isEqualTo(2);
        assertThat(t1.getRevoked()).isTrue();
        assertThat(t2.getRevoked()).isTrue();
    }
}

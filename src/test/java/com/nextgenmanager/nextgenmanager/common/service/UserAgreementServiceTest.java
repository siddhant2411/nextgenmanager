package com.nextgenmanager.nextgenmanager.common.service;

import com.nextgenmanager.nextgenmanager.common.dto.auth.AuthAgreementResponse;
import com.nextgenmanager.nextgenmanager.common.model.AppUser;
import com.nextgenmanager.nextgenmanager.common.model.UserAgreementAcceptance;
import com.nextgenmanager.nextgenmanager.common.repository.AppUserRepository;
import com.nextgenmanager.nextgenmanager.common.repository.UserAgreementAcceptanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAgreementServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private UserAgreementAcceptanceRepository acceptanceRepository;

    private UserAgreementService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        // The real bundled agreement, so a missing or misnamed resource fails here and not in production.
        service = new UserAgreementService(appUserRepository, acceptanceRepository, new DefaultResourceLoader(),
                "1.0", "User Agreement", "classpath:agreement/user-agreement.html");
        user = new AppUser();
        user.setId(7L);
        user.setUsername("ravi");
        lenient().when(appUserRepository.findByUsernameAndDeletedDateIsNull("ravi")).thenReturn(Optional.of(user));
    }

    @Test
    void getAgreement_newUser_isNotAcceptedAndCarriesTheText() {
        when(acceptanceRepository.findByAppUser_IdAndAgreementVersion(7L, "1.0")).thenReturn(Optional.empty());

        AuthAgreementResponse response = service.getAgreement("ravi");

        assertThat(response.accepted()).isFalse();
        assertThat(response.version()).isEqualTo("1.0");
        assertThat(response.content()).contains("Acceptable use");
    }

    @Test
    void accept_currentVersion_recordsWhoWhenAndWhere() {
        when(acceptanceRepository.findByAppUser_IdAndAgreementVersion(7L, "1.0")).thenReturn(Optional.empty());

        service.accept("ravi", "1.0", "203.0.113.9", "Mozilla/5.0");

        ArgumentCaptor<UserAgreementAcceptance> captor = ArgumentCaptor.forClass(UserAgreementAcceptance.class);
        verify(acceptanceRepository).save(captor.capture());
        assertThat(captor.getValue().getAppUser().getId()).isEqualTo(7L);
        assertThat(captor.getValue().getAgreementVersion()).isEqualTo("1.0");
        assertThat(captor.getValue().getIpAddress()).isEqualTo("203.0.113.9");
        assertThat(captor.getValue().getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(captor.getValue().getAcceptedDate()).isNotNull();
    }

    @Test
    void accept_twice_keepsTheFirstRecord() {
        UserAgreementAcceptance existing = new UserAgreementAcceptance();
        existing.setAcceptedDate(new Date(0));
        when(acceptanceRepository.findByAppUser_IdAndAgreementVersion(7L, "1.0")).thenReturn(Optional.of(existing));

        AuthAgreementResponse response = service.accept("ravi", "1.0", "203.0.113.9", "Mozilla/5.0");

        verify(acceptanceRepository, never()).save(any());
        assertThat(response.accepted()).isTrue();
        assertThat(response.acceptedDate()).isEqualTo(new Date(0));
    }

    @Test
    void accept_staleVersion_isRefusedWithConflict() {
        assertThatThrownBy(() -> service.accept("ravi", "0.9", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(acceptanceRepository, never()).save(any());
    }
}

package com.nextgenmanager.nextgenmanager.items.service;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ItemRevision;
import com.nextgenmanager.nextgenmanager.items.model.ItemRevisionStatus;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.items.repository.ItemRevisionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Only a revision that was never released may be deleted; released history stays. */
class ItemRevisionDeleteTest {

    private final ItemRevisionRepository revisions = mock(ItemRevisionRepository.class);
    private final ItemRevisionServiceImpl service = new ItemRevisionServiceImpl(revisions, mock(InventoryItemRepository.class));

    @AfterEach
    void clearUser() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aDraftIsDeletedByEngineering() {
        signInAs("ROLE_ENGINEERING");
        ItemRevision draft = revision(11L, "B", ItemRevisionStatus.DRAFT);

        service.deleteUnreleased(11L);

        verify(revisions).delete(draft);
    }

    @Test
    void aRevisionAwaitingApprovalIsDeletedByAnApproverOnly() {
        ItemRevision pending = revision(12L, "B", ItemRevisionStatus.PENDING_APPROVAL);

        signInAs("ROLE_ENGINEERING");
        assertThatThrownBy(() -> service.deleteUnreleased(12L)).hasMessageContaining("Not permitted");
        verify(revisions, never()).delete(any());

        signInAs("ROLE_INVENTORY_ADMIN");
        service.deleteUnreleased(12L);
        verify(revisions).delete(pending);
    }

    @ParameterizedTest
    @EnumSource(value = ItemRevisionStatus.class, names = {"RELEASED", "SUPERSEDED", "OBSOLETE"})
    void aRevisionThatWasEverReleasedIsNeverDeleted(ItemRevisionStatus status) {
        signInAs("ROLE_SUPER_ADMIN");
        revision(13L, "A", status);

        assertThatThrownBy(() -> service.deleteUnreleased(13L)).hasMessageContaining("never released");
        verify(revisions, never()).delete(any());
    }

    @Test
    void aDraftPinnedOnABomIsKept() {
        signInAs("ROLE_ADMIN");
        revision(14L, "C", ItemRevisionStatus.DRAFT);
        when(revisions.countBomPositionsPinning(14L)).thenReturn(2L);

        assertThatThrownBy(() -> service.deleteUnreleased(14L)).hasMessageContaining("2 BOM position");
        verify(revisions, never()).delete(any());
    }

    private ItemRevision revision(Long id, String code, ItemRevisionStatus status) {
        InventoryItem item = new InventoryItem();
        item.setInventoryItemId(7);
        ItemRevision current = new ItemRevision();
        current.setId(1L);
        current.setStatus(ItemRevisionStatus.RELEASED);
        item.setCurrentRevision(current);

        ItemRevision rev = new ItemRevision();
        rev.setId(id);
        rev.setRevisionCode(code);
        rev.setStatus(status);
        rev.setInventoryItem(item);
        when(revisions.findById(id)).thenReturn(Optional.of(rev));
        return rev;
    }

    private static void signInAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("tester", null, List.of(new SimpleGrantedAuthority(role))));
    }
}

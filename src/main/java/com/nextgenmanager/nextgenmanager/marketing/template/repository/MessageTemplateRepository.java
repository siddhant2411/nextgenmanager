package com.nextgenmanager.nextgenmanager.marketing.template.repository;

import com.nextgenmanager.nextgenmanager.marketing.template.model.MessageTemplate;
import com.nextgenmanager.nextgenmanager.marketing.template.model.TemplateChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, Long> {

    List<MessageTemplate> findByDeletedDateIsNullOrderByNameAsc();

    List<MessageTemplate> findByChannelAndDeletedDateIsNullOrderByNameAsc(TemplateChannel channel);
}
